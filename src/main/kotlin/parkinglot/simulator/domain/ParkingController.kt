package parkinglot.simulator.domain

import arrow.core.raise.either
import arrow.fx.coroutines.parZip
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import parkinglot.simulator.domain.service.ParkingLifeCycleService
import parkinglot.simulator.domain.connector.LicensePlateReader
import parkinglot.simulator.domain.connector.ParkingGuardNotifier
import parkinglot.simulator.domain.connector.PaymentStatusChecker
import parkinglot.simulator.domain.connector.SensorEventHandler
import parkinglot.simulator.domain.connector.VehicleSizeEstimator
import parkinglot.simulator.domain.model.DenyEntryReason
import parkinglot.simulator.domain.model.SensorEvent
import parkinglot.simulator.domain.repository.VehicleTransitRepository

@Service
class ParkingController(
    private val licensePlateReader: LicensePlateReader,
    private val vehicleSizeEstimator: VehicleSizeEstimator,
    private val paymentStatusChecker: PaymentStatusChecker,
    private val parkingGuardNotifier: ParkingGuardNotifier,
    private val vehicleTransitRepository: VehicleTransitRepository,
    private val parkingLifecycleService: ParkingLifeCycleService
) : SensorEventHandler {
    private val vehicleEnteringMutex = Mutex()

    override suspend fun handle(event: SensorEvent) {
        logger.info("Handling sensor event {}", event)
        when (event) {
            is SensorEvent.VehicleEnteringEvent -> handleVehicleEntering()
            is SensorEvent.VehicleLeavingEvent ->
                vehicleTransitRepository.addVehicleInTransit(event.licensePlate)
            is SensorEvent.ParkingSpotOccupiedEvent ->
                parkingLifecycleService.occupyParkingSpot(event.licensePlate, event.spotId)
            is SensorEvent.ParkingSpotReleasedEvent ->
                parkingLifecycleService.releaseParkingSpot(event.licensePlate, event.spotId)
            is SensorEvent.OverStayingEvent -> parkingGuardNotifier.vehicleHasOverStayed(
                event.licensePlate,
                event.spotId,
                event.duration
            )
        }
    }

    // We can assume that the vehicle entering events are sequential, See README.
    private suspend fun handleVehicleEntering() = vehicleEnteringMutex.withLock {
        parZip(
            { licensePlateReader.read() },
            { vehicleSizeEstimator.isVehicleTooBig() },
            { paymentStatusChecker.isPaymentComplete() }
        ) { plate, size, payment ->
            either {
                size.bind()
                payment.bind()
                plate.bind()
            }
        }.fold(
            { reason -> parkingGuardNotifier.denyEntry(reason) },
            { plate ->
                if (!parkingLifecycleService.reserveIfCapacityAvailable(plate)) {
                    // This happens only in very rare occasions because people can't go to the payment machine if
                    // there are no available parking spots. However sometimes the sensor system make mistakes.
                    // If it happens the client will get his money back unless he/she decides to wait for a spot to be released.
                    parkingGuardNotifier.denyEntry(DenyEntryReason.NO_AVAILABLE_PARKING_SPOTS)
                }
            }
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ParkingController::class.java)
    }
}
