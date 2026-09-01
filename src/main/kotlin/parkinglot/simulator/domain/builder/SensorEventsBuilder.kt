package parkinglot.simulator.domain.builder

import kotlin.time.Duration
import parkinglot.simulator.domain.model.LicensePlate
import parkinglot.simulator.domain.model.ParkingSpotId
import parkinglot.simulator.domain.model.SensorEvent

class SensorEventsBuilder {
    private val events = mutableListOf<SensorEvent>()

    fun vehicleEntering() {
        events.add(SensorEvent.VehicleEnteringEvent())
    }

    fun occupied(licensePlates: List<LicensePlate>, spotIds: List<ParkingSpotId>) =
        createMultipleEvents(licensePlates, spotIds, ::occupied)

    fun occupied(licensePlate: LicensePlate, spotId: ParkingSpotId) {
        events.add(SensorEvent.ParkingSpotOccupiedEvent(licensePlate, spotId))
    }

    fun released(licensePlates: List<LicensePlate>, spotIds: List<ParkingSpotId>) =
        createMultipleEvents(licensePlates, spotIds, ::released)

    fun released(licensePlate: LicensePlate, spotId: ParkingSpotId) {
        events.add(SensorEvent.ParkingSpotReleasedEvent(licensePlate, spotId))
    }

    fun leaving(licensePlates: List<LicensePlate>, spotIds: List<ParkingSpotId>) =
        createMultipleEvents(licensePlates, spotIds, ::leaving)

    fun leaving(licensePlate: LicensePlate, spotId: ParkingSpotId) {
        events.add(SensorEvent.VehicleLeavingEvent(licensePlate, spotId))
    }

    fun overstaying(licensePlates: List<LicensePlate>, spotIds: List<ParkingSpotId>, duration: Duration) =
        createMultipleEvents(licensePlates, spotIds) { plate, spotId -> overstaying(plate, spotId, duration) }

    fun overstaying(licensePlate: LicensePlate, spotId: ParkingSpotId, duration: Duration) {
        events.add(SensorEvent.OverStayingEvent(licensePlate, spotId, duration))
    }

    fun build(): List<SensorEvent> = events

    private fun createMultipleEvents(
        licensePlates: List<LicensePlate>,
        spotIds: List<ParkingSpotId>,
        action: (LicensePlate, ParkingSpotId) -> Unit
    ) {
        require(licensePlates.size == spotIds.size) {
            "Expected ${spotIds.size} license plates, but got ${licensePlates.size}"
        }
        require(licensePlates.distinct().size == licensePlates.size) {
            "License plates must be distinct, but got $licensePlates"
        }

        licensePlates.zip(spotIds).forEach { (plate, spotId) -> action(plate, spotId) }
    }
}
