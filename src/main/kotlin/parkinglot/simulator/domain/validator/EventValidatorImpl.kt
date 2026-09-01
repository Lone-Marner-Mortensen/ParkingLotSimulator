package parkinglot.simulator.domain.validator

import org.springframework.stereotype.Component
import parkinglot.simulator.domain.model.SensorEvent
import parkinglot.simulator.domain.repository.ParkingSpotRepository
import parkinglot.simulator.domain.repository.VehicleTransitRepository

@Component
class EventValidatorImpl(
    private val vehicleTransitRepository: VehicleTransitRepository,
    private val parkingSpotRepository: ParkingSpotRepository
) : EventValidator {

    override fun isValid(event: SensorEvent): Boolean = when (event) {
        is SensorEvent.VehicleEnteringEvent -> true
        // Check if the vehicle is in transit before occupying a parking spot
        is SensorEvent.ParkingSpotOccupiedEvent -> vehicleTransitRepository.existsByLicensePlate(event.licensePlate.value)
        // Check if the parking spot has been taken before processing vehicle leaving or overstaying events
        is SensorEvent.ParkingSpotReleasedEvent -> parkingSpotRepository.existsBySpotId(event.spotId.value)
        is SensorEvent.VehicleLeavingEvent -> parkingSpotRepository.existsBySpotId(event.spotId.value)
        is SensorEvent.OverStayingEvent -> parkingSpotRepository.existsBySpotId(event.spotId.value)
    }
}
