package parkinglot.simulator.domain.connector

import arrow.core.Either
import parkinglot.simulator.domain.model.DenyEntryReason

interface VehicleSizeEstimator {
    suspend fun isVehicleTooBig(): Either<DenyEntryReason, Boolean>
}
