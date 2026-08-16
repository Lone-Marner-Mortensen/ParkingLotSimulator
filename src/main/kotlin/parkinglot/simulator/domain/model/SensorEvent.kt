package parkinglot.simulator.domain.model

import kotlin.time.Duration

// **************************************************************************//
// In the real world those definitions would be a part of the sensor-system. //
// But the sensor-system is abstracted away.                                 //
// **************************************************************************//


@JvmInline
value class ParkingSpotId(val value: String)

@JvmInline
value class LicensePlate(val value: String)

sealed interface SensorEvent {
    data class VehicleEnteringEvent(val location: VehicleRegistrationLocation) : SensorEvent
    data class ParkingSpotOccupiedEvent(val spotId: ParkingSpotId) : SensorEvent
    data class ParkingSpotReleasedEvent(val spotId: ParkingSpotId) : SensorEvent
    data class VehicleLeavingEvent(val spotId: ParkingSpotId) : SensorEvent
    data class OverStayingEvent(val plateNumber: LicensePlate, val spotId: ParkingSpotId, val duration: Duration) : SensorEvent
}
