package parkinglot.simulator.domain.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import parkinglot.simulator.domain.repository.ParkingSpotRepository
import parkinglot.simulator.domain.repository.VehicleTransitRepository

// Ensures atomicity for admission, occupation, and release of parking spots

@Service
class ParkingLifeCycleService(
    private val parkingSpotRepository: ParkingSpotRepository,
    private val vehicleTransitRepository: VehicleTransitRepository
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
    fun occupyParkingSpot(licensePlate: String, spotId: String) {
        parkingSpotRepository.occupyParkingSpot(licensePlate, spotId)
        vehicleTransitRepository.removeVehicleInTransit(licensePlate)
    }

    @Transactional
    fun releaseParkingSpot(licensePlate: String, spotId: String) {
        parkingSpotRepository.releaseParkingSpot(spotId)
        vehicleTransitRepository.removeVehicleInTransit(licensePlate)
    }
}
