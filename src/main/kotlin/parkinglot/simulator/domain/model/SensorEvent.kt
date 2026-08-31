package parkinglot.simulator.domain.model

import kotlin.time.Duration
import java.util.UUID

sealed interface SensorEvent {
    val eventId: String

    data class VehicleEnteringEvent(
        override val eventId: String = newEventId()
    ) : SensorEvent

    data class ParkingSpotOccupiedEvent(
        val licensePlate: LicensePlate,
        val spotId: ParkingSpotId,
        override val eventId: String = newEventId()
    ) : SensorEvent

    data class ParkingSpotReleasedEvent(
        val licensePlate: LicensePlate,
        val spotId: ParkingSpotId,
        override val eventId: String = newEventId()
    ) : SensorEvent

    data class VehicleLeavingEvent(
        val licensePlate: LicensePlate,
        val spotId: ParkingSpotId,
        override val eventId: String = newEventId()
    ) : SensorEvent

    data class OverStayingEvent(
        val licensePlate: LicensePlate,
        val spotId: ParkingSpotId,
        val duration: Duration,
        override val eventId: String = newEventId()
    ) : SensorEvent

    companion object {
        private fun newEventId(): String = UUID.randomUUID().toString()
    }
}
