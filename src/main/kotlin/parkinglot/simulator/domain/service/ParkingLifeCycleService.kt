package parkinglot.simulator.domain.service

import arrow.core.raise.either
import arrow.fx.coroutines.parZip
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import parkinglot.simulator.domain.repository.ProcessingStatusSensorEventRepository
import parkinglot.simulator.domain.connector.LicensePlateReader
import parkinglot.simulator.domain.connector.ParkingGuardNotifier
import parkinglot.simulator.domain.connector.PaymentStatusChecker
import parkinglot.simulator.domain.connector.VehicleSizeEstimator
import parkinglot.simulator.domain.model.DenyEntryReason
import parkinglot.simulator.domain.model.SensorEvent.OverStayingEvent
import parkinglot.simulator.domain.model.SensorEvent.ParkingSpotOccupiedEvent
import parkinglot.simulator.domain.model.SensorEvent.ParkingSpotReleasedEvent
import parkinglot.simulator.domain.model.SensorEvent.VehicleEnteringEvent
import parkinglot.simulator.domain.model.SensorEvent.VehicleLeavingEvent
import parkinglot.simulator.domain.repository.ParkingSpotRepository
import parkinglot.simulator.domain.repository.VehicleTransitRepository
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

@Service
open class ParkingLifeCycleService(
    private val parkingSpotRepository: ParkingSpotRepository,
    private val vehicleTransitRepository: VehicleTransitRepository,
    private val processingStatusSensorEventRepository: ProcessingStatusSensorEventRepository,
    private val parkingGuardNotifier: ParkingGuardNotifier,
    private val licensePlateReader: LicensePlateReader,
    private val vehicleSizeEstimator: VehicleSizeEstimator,
    private val paymentStatusChecker: PaymentStatusChecker,
    private val meterRegistry: MeterRegistry
) {
     // using locks is not scalable (if you want multiple JVM instances), but I don't want complex db code and
     // scalability is not relevant for problems like this.
    private val reservationMutex = Mutex()
    private val vehicleEnteringMutex = Mutex()

    // We can assume that the vehicle entering events are Synchronously, See README (Only one entering lane).
    suspend fun handleVehicleEntering(event: VehicleEnteringEvent) = vehicleEnteringMutex.withLock {
        try {
            either {
                parZip(
                    { licensePlateReader.read().bind() },
                    { vehicleSizeEstimator.isVehicleTooBig().bind() },
                    { paymentStatusChecker.wasPaymentSuccessful().bind() }
                ) { plate, _, _ -> plate }
            }.fold(
                { reason -> parkingGuardNotifier.denyEntry(reason) },
                { plate ->
                    // If there are no spots available, the car will probably not wait long.
                    // There are only one lane, so he will be the first in the queue. There are also many spots.
                    if (!reserveCapacityWithRetry(plate.value)) {
                        parkingGuardNotifier.denyEntry(DenyEntryReason.NO_AVAILABLE_PARKING_SPOTS)
                    }
                }
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            // ParkingLotManager launches this fire-and-forget, so a failure here can't propagate back to
            // the SensorEventAdapter's retry/redelivery logic. We therefore catch and record it here.
            meterRegistry.counter("parking.controller.vehicle_entering", "outcome", "failed").increment()
            logger.error("Handling vehicle entering event {} failed", event.eventId, exception)
        } finally {
            processingStatusSensorEventRepository.setProcessingStatusToCompleted(event, Instant.now())
        }
    }

    private suspend fun reserveCapacityWithRetry(plate: String): Boolean {
        repeat(CAPACITY_RETRY_ATTEMPTS) { attempt ->
            if (reserveIfCapacityAvailable(plate)) {
                return true
            }
            logger.info("Waiting for admission to enter parking lot")
            if (attempt < CAPACITY_RETRY_ATTEMPTS - 1) {
                delay(CAPACITY_RETRY_DELAY)
            }
        }
        return false
    }

    private suspend fun reserveIfCapacityAvailable(licensePlate: String): Boolean = reservationMutex.withLock {
        val availableCapacity = parkingSpotRepository.getFreeParkingSpots().size -
            vehicleTransitRepository.getNumberOfVehiclesInTransit()

        if (availableCapacity <= 0) {
            return false
        }

        vehicleTransitRepository.addVehicleInTransit(licensePlate)
        true
    }

    @Transactional
    open fun occupyParkingSpot(event: ParkingSpotOccupiedEvent) {
        parkingSpotRepository.occupyParkingSpot(event.licensePlate.value, event.spotId.value)
        vehicleTransitRepository.removeVehicleInTransit(event.licensePlate.value)
        processingStatusSensorEventRepository.setProcessingStatusToCompleted(event, Instant.now())
    }

    @Transactional
    open fun releaseParkingSpot(event: ParkingSpotReleasedEvent) {
        parkingSpotRepository.releaseParkingSpot(event.spotId.value)
        vehicleTransitRepository.removeVehicleInTransit(event.licensePlate.value)
        processingStatusSensorEventRepository.setProcessingStatusToCompleted(event, Instant.now())
    }

    @Transactional
    open fun markVehicleAsLeaving(event: VehicleLeavingEvent) {
        vehicleTransitRepository.addVehicleInTransit(event.licensePlate.value)
        processingStatusSensorEventRepository.setProcessingStatusToCompleted(event, Instant.now())
    }

    @Transactional
    open fun overStaying(event: OverStayingEvent) {
        parkingGuardNotifier.vehicleHasOverStayed(event.licensePlate.value, event.spotId.value, event.duration)
        processingStatusSensorEventRepository.setProcessingStatusToCompleted(event, Instant.now())
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ParkingLifeCycleService::class.java)
        private const val CAPACITY_RETRY_ATTEMPTS = 15
        private val CAPACITY_RETRY_DELAY = 30.seconds
    }
}
