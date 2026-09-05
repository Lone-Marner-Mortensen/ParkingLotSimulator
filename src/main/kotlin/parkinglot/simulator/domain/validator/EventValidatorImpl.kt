package parkinglot.simulator.domain.validator

import org.springframework.stereotype.Component
import parkinglot.simulator.domain.model.SensorEvent
import parkinglot.simulator.domain.repository.ParkingSpotRepository
import parkinglot.simulator.domain.repository.VehicleTransitRepository
import parkinglot.simulator.domain.model.SensorEvent.VehicleEnteringEvent
import parkinglot.simulator.domain.model.SensorEvent.ParkingSpotOccupiedEvent
import parkinglot.simulator.domain.model.SensorEvent.ParkingSpotReleasedEvent
import parkinglot.simulator.domain.model.SensorEvent.VehicleLeavingEvent
import parkinglot.simulator.domain.model.SensorEvent.OverStayingEvent

@Component
class EventValidatorImpl(
    private val vehicleTransitRepository: VehicleTransitRepository,
    private val parkingSpotRepository: ParkingSpotRepository
) : EventValidator {

    override fun isValid(event: SensorEvent): Boolean = when (event) {
        is VehicleEnteringEvent -> true
        // Check if the vehicle is in transit before occupying a parking spot
        is ParkingSpotOccupiedEvent -> vehicleTransitRepository.existsByLicensePlate(event.licensePlate.value)
        // Check if the parking spot has been taken before processing vehicle leaving or overstaying events
        is ParkingSpotReleasedEvent -> parkingSpotRepository.existsBySpotId(event.spotId.value)
        is VehicleLeavingEvent -> parkingSpotRepository.existsBySpotId(event.spotId.value)
        is OverStayingEvent -> parkingSpotRepository.existsBySpotId(event.spotId.value)
    }
}
