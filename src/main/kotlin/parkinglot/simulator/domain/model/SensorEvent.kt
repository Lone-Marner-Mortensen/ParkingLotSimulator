package parkinglot.simulator.domain.model

import kotlin.time.Duration
import java.util.UUID

sealed interface SensorEvent {
    val eventId: String

    data class VehicleEnteringEvent(
        override val eventId: String = newEventId()
    ) : SensorEvent

    data class ParkingSpotOccupiedEvent(
        val licensePlate: String,
        val spotId: String,
        override val eventId: String = newEventId()
    ) : SensorEvent

    data class ParkingSpotReleasedEvent(
        val licensePlate: String,
        val spotId: String,
        override val eventId: String = newEventId()
    ) : SensorEvent

    data class VehicleLeavingEvent(
        val licensePlate: String,
        val spotId: String,
        override val eventId: String = newEventId()
    ) : SensorEvent

    data class OverStayingEvent(
        val licensePlate: String,
        val spotId: String,
        val duration: Duration,
        override val eventId: String = newEventId()
    ) : SensorEvent

    companion object {
        private fun newEventId(): String = UUID.randomUUID().toString()
    }
}
