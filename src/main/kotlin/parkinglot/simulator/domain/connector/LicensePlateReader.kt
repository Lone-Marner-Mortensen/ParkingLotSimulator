package parkinglot.simulator.domain.connector

import arrow.core.Either
import parkinglot.simulator.domain.model.DenyEntryReason
import parkinglot.simulator.domain.model.LicensePlate

interface LicensePlateReader {
    suspend fun read(): Either<DenyEntryReason, LicensePlate>
}
