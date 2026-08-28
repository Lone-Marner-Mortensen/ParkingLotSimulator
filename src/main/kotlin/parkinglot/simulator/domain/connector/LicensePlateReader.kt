package parkinglot.simulator.domain.connector

import arrow.core.Either
import parkinglot.simulator.domain.model.DenyEntryReason

interface LicensePlateReader {
    suspend fun read(): Either<DenyEntryReason, String>
}
