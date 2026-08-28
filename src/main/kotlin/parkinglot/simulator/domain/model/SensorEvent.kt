package parkinglot.simulator.domain.model

import kotlin.time.Duration


@JvmInline
value class ParkingSpotId(val value: String)

@JvmInline
value class LicensePlate(val value: String)

sealed interface SensorEvent {
    data object VehicleEnteringEvent : SensorEvent
    data class ParkingSpotOccupiedEvent(val licensePlate: LicensePlate, val spotId: ParkingSpotId) : SensorEvent
    data class ParkingSpotReleasedEvent(val licensePlate: LicensePlate, val spotId: ParkingSpotId) : SensorEvent
    data class VehicleLeavingEvent(val licensePlate: LicensePlate, val spotId: ParkingSpotId) : SensorEvent
    data class OverStayingEvent(val licensePlate: LicensePlate, val spotId: ParkingSpotId, val duration: Duration) : SensorEvent
}
