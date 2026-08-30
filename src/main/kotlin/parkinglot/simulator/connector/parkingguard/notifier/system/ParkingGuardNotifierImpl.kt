package parkinglot.simulator.connector.parkingguard.notifier.system

import org.springframework.stereotype.Component

import parkinglot.simulator.domain.connector.ParkingGuardNotifier
import parkinglot.simulator.domain.model.DenyEntryReason
import kotlin.time.Duration


@Component
class ParkingGuardNotifierImpl : ParkingGuardNotifier {

    override fun denyEntry(reason: DenyEntryReason) {
        // send reason to parking guard
    }

    override fun vehicleHasOverStayed(licensePlate: String, parkingSpotId: String, duration: Duration) {
        // send information to parking guard about which vehicle has overstayed and for how long
    }
}
