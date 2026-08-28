package parkinglot.simulator.fakeservice

import arrow.core.Either
import parkinglot.simulator.domain.connector.VehicleSizeEstimator
import parkinglot.simulator.domain.model.DenyEntryReason

object FakeVehicleSizeEstimator : VehicleSizeEstimator {
    override suspend fun isVehicleTooBig(): Either<DenyEntryReason, Boolean> = throw NotImplementedError()
}
