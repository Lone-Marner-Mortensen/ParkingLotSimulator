package parkinglot.simulator.connector.parkingguard.notifier.system

import parkinglot.simulator.domain.model.LicensePlate
import parkinglot.simulator.domain.model.ParkingSpotId
import parkinglot.simulator.domain.connector.ParkingGuardNotifier
import parkinglot.simulator.domain.model.DenyEntryReason
import kotlin.time.Duration


class ParkingGuardNotifierImpl : ParkingGuardNotifier {

    override fun denyEntry(reason: DenyEntryReason) {
        // send reason to parking guard
    }

    override fun vehicleHasOverStayed(licensePlate: LicensePlate, parkingSpotId: ParkingSpotId, duration: Duration) {
        // send information to parking guard about which vehicle has overstayed and for how long
    }
}
