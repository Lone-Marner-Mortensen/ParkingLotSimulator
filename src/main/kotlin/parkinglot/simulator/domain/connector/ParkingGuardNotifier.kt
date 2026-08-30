package parkinglot.simulator.domain.connector

import parkinglot.simulator.domain.model.DenyEntryReason
import kotlin.time.Duration

interface ParkingGuardNotifier {

    fun denyEntry(reason: DenyEntryReason)

    fun vehicleHasOverStayed(licensePlate: String, parkingSpotId: String, duration: Duration)
}
