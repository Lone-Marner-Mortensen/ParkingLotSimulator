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

    fun occupied(licensePlate: LicensePlate, spotId: ParkingSpotId) {
        events.add(SensorEvent.ParkingSpotOccupiedEvent(licensePlate, spotId))
    }

    fun occupied(licensePlate: LicensePlate, spotIds: List<ParkingSpotId>) =
        createMultipleEvents(licensePlate, spotIds, ::occupied)

    fun occupied(spotIds: List<ParkingSpotId>) =
        createMultipleEvents(randomLicensePlates(spotIds.size), spotIds, ::occupied)

    fun occupied(licensePlates: List<LicensePlate>, spotIds: List<ParkingSpotId>) =
        createMultipleEvents(licensePlates, spotIds, ::occupied)

    fun released(licensePlate: LicensePlate, spotId: ParkingSpotId) {
        events.add(SensorEvent.ParkingSpotReleasedEvent(licensePlate, spotId))
    }

    fun released(licensePlate: LicensePlate, spotIds: List<ParkingSpotId>) =
        createMultipleEvents(licensePlate, spotIds, ::released)

    fun released(spotIds: List<ParkingSpotId>) =
        createMultipleEvents(randomLicensePlates(spotIds.size), spotIds, ::released)

    fun released(licensePlates: List<LicensePlate>, spotIds: List<ParkingSpotId>) =
        createMultipleEvents(licensePlates, spotIds, ::released)

    fun leaving(licensePlate: LicensePlate, spotId: ParkingSpotId) {
        events.add(SensorEvent.VehicleLeavingEvent(licensePlate, spotId))
    }

    fun leaving(licensePlate: LicensePlate, spotIds: List<ParkingSpotId>) =
        createMultipleEvents(licensePlate, spotIds, ::leaving)

    fun leaving(spotIds: List<ParkingSpotId>) =
        createMultipleEvents(randomLicensePlates(spotIds.size), spotIds, ::leaving)

    fun leaving(licensePlates: List<LicensePlate>, spotIds: List<ParkingSpotId>) =
        createMultipleEvents(licensePlates, spotIds, ::leaving)

    fun overstaying(licensePlate: LicensePlate, spotId: ParkingSpotId, duration: Duration) {
        events.add(SensorEvent.OverStayingEvent(licensePlate, spotId, duration))
    }

    fun overstaying(licensePlate: LicensePlate, spotIds: List<ParkingSpotId>, duration: Duration) =
        createMultipleEvents(licensePlate, spotIds) { plate, spotId -> overstaying(plate, spotId, duration) }

    fun overstaying(spotIds: List<ParkingSpotId>, duration: Duration) =
        createMultipleEvents(randomLicensePlates(spotIds.size), spotIds) { plate, spotId -> overstaying(plate, spotId, duration) }

    fun overstaying(licensePlates: List<LicensePlate>, spotIds: List<ParkingSpotId>, duration: Duration) =
        createMultipleEvents(licensePlates, spotIds) { plate, spotId -> overstaying(plate, spotId, duration) }

    fun build(): List<SensorEvent> = events

    private fun randomLicensePlate(): LicensePlate =
        LicensePlate((1..10).map { LICENSE_PLATE_CHARS.random() }.joinToString(""))

    private fun randomLicensePlates(count: Int): List<LicensePlate> {
        val plates = mutableSetOf<LicensePlate>()
        while (plates.size < count) {
            plates.add(randomLicensePlate())
        }
        return plates.toList()
    }

    private fun createMultipleEvents(
        licensePlate: LicensePlate,
        spotIds: List<ParkingSpotId>,
        action: (LicensePlate, ParkingSpotId) -> Unit
    ) = spotIds.forEach { action(licensePlate, it) }

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

    companion object {
        private const val LICENSE_PLATE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    }
}
