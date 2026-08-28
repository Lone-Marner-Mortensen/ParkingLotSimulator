package parkinglot.simulator.fakeservice

import arrow.core.Either
import parkinglot.simulator.domain.connector.LicensePlateReader
import parkinglot.simulator.domain.model.DenyEntryReason

object FakeLicensePlateReader : LicensePlateReader {
    override suspend fun read(): Either<DenyEntryReason, String> = throw NotImplementedError()
}
