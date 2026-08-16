package parkinglot.simulator.connector.sensor.system

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.delay
import parkinglot.simulator.domain.model.DenyEntryReason
import parkinglot.simulator.domain.model.VehicleRegistrationLocation
import parkinglot.simulator.domain.connector.VehicleSizeEstimator

class VehicleSizeEstimatorImpl: VehicleSizeEstimator {

    override suspend fun isVehicleTooBig(position: VehicleRegistrationLocation): Either<DenyEntryReason, Boolean> {
        delay(500)
        // ToDo: Make output random with 95% chance of returning true and 5% chance of returning false
        val tooBig = false
        return if (tooBig) DenyEntryReason.VEHICLE_TOO_BIG.left() else true.right()
    }

}
