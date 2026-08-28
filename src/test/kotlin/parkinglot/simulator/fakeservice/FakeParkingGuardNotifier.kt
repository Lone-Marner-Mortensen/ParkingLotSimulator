package parkinglot.simulator.fakeservice

import parkinglot.simulator.domain.connector.ParkingGuardNotifier
import parkinglot.simulator.domain.model.DenyEntryReason
import parkinglot.simulator.domain.model.LicensePlate
import parkinglot.simulator.domain.model.ParkingSpotId
import kotlin.time.Duration

class FakeParkingGuardNotifier : ParkingGuardNotifier {
    override fun denyEntry(reason: DenyEntryReason) {}
    override fun vehicleHasOverStayed(licensePlate: LicensePlate, parkingSpotId: ParkingSpotId, duration: Duration) {}
}
