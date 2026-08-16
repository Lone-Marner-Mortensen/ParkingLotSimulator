package parkinglot.simulator.domain.connector

import parkinglot.simulator.domain.model.LicensePlate
import parkinglot.simulator.domain.model.ParkingSpotId
import parkinglot.simulator.domain.model.DenyEntryReason
import parkinglot.simulator.domain.model.VehicleRegistrationLocation
import kotlin.time.Duration

interface ParkingGuardNotifier {

    fun denyEntry(position: VehicleRegistrationLocation, reason: DenyEntryReason)

    fun vehicleHasOverStayed(licensePlate: LicensePlate, parkingSpotId: ParkingSpotId, duration: Duration)

}
