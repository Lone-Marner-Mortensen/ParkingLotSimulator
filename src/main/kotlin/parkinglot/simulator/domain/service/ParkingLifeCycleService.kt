package parkinglot.simulator.domain.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import parkinglot.simulator.connector.sensor.system.adapter.ProcessingStatusSensorEventRepository
import parkinglot.simulator.domain.connector.ParkingGuardNotifier
import parkinglot.simulator.domain.model.SensorEvent.OverStayingEvent
import parkinglot.simulator.domain.model.SensorEvent.ParkingSpotOccupiedEvent
import parkinglot.simulator.domain.model.SensorEvent.ParkingSpotReleasedEvent
import parkinglot.simulator.domain.model.SensorEvent.VehicleLeavingEvent
import parkinglot.simulator.domain.repository.ParkingSpotRepository
import parkinglot.simulator.domain.repository.VehicleTransitRepository
import java.time.Instant

@Service
class ParkingLifeCycleService(
    private val parkingSpotRepository: ParkingSpotRepository,
    private val vehicleTransitRepository: VehicleTransitRepository,
    private val processingStatusSensorEventRepository: ProcessingStatusSensorEventRepository,
    private val parkingGuardNotifier: ParkingGuardNotifier
) {
    private val reservationMutex = Mutex()

    // We can assume that the vehicle entering events are Synchronously, See README.
    suspend fun reserveIfCapacityAvailable(licensePlate: String): Boolean = reservationMutex.withLock {
        val availableCapacity = parkingSpotRepository.getFreeParkingSpots().size -
            vehicleTransitRepository.getNumberOfVehiclesInTransit()

        if (availableCapacity <= 0) {
            return false
        }

        vehicleTransitRepository.addVehicleInTransit(licensePlate)
        true
    }

    @Transactional
    fun occupyParkingSpot(event: ParkingSpotOccupiedEvent) {
        parkingSpotRepository.occupyParkingSpot(event.licensePlate.value, event.spotId.value)
        vehicleTransitRepository.removeVehicleInTransit(event.licensePlate.value)
        processingStatusSensorEventRepository.setProcessingStatusToCompleted(event, Instant.now())
    }

    @Transactional
    fun releaseParkingSpot(event: ParkingSpotReleasedEvent) {
        parkingSpotRepository.releaseParkingSpot(event.spotId.value)
        vehicleTransitRepository.removeVehicleInTransit(event.licensePlate.value)
        processingStatusSensorEventRepository.setProcessingStatusToCompleted(event, Instant.now())
    }

    @Transactional
    fun markVehicleAsLeaving(event: VehicleLeavingEvent) {
        vehicleTransitRepository.addVehicleInTransit(event.licensePlate.value)
        processingStatusSensorEventRepository.setProcessingStatusToCompleted(event, Instant.now())
    }

    @Transactional
    fun overStaying(event: OverStayingEvent) {
        parkingGuardNotifier.vehicleHasOverStayed(event.licensePlate.value, event.spotId.value, event.duration)
        processingStatusSensorEventRepository.setProcessingStatusToCompleted(event, Instant.now())
    }
}
