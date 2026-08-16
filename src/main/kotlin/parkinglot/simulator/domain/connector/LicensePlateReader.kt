package parkinglot.simulator.domain.connector

import arrow.core.Either
import parkinglot.simulator.domain.model.DenyEntryReason
import parkinglot.simulator.domain.model.VehicleRegistrationLocation

interface LicensePlateReader {
    suspend fun read(position: VehicleRegistrationLocation): Either<DenyEntryReason, String>
}
