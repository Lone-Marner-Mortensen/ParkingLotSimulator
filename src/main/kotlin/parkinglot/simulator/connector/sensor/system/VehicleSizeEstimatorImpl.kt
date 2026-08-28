package parkinglot.simulator.connector.sensor.system

import arrow.core.Either
import arrow.core.right
import kotlinx.coroutines.delay
import parkinglot.simulator.domain.model.DenyEntryReason
import parkinglot.simulator.domain.connector.VehicleSizeEstimator

class VehicleSizeEstimatorImpl: VehicleSizeEstimator {

    override suspend fun isVehicleTooBig(): Either<DenyEntryReason, Boolean> {

        delay(500)

        // simulate the vehicle size estimation process in a predictable way
        return false.right()
    }
}
