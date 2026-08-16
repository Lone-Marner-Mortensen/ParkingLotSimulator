package parkinglot.simulator.domain.connector

import arrow.core.Either
import parkinglot.simulator.domain.model.DenyEntryReason
import parkinglot.simulator.domain.model.VehicleRegistrationLocation

interface VehicleSizeEstimator {
    suspend fun isVehicleTooBig(position: VehicleRegistrationLocation): Either<DenyEntryReason, Boolean>
}
