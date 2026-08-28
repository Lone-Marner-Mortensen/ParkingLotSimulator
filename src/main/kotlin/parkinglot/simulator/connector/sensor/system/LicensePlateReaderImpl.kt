package parkinglot.simulator.connector.sensor.system

import arrow.core.Either
import arrow.core.right
import kotlinx.coroutines.delay
import kotlin.random.Random
import parkinglot.simulator.domain.model.DenyEntryReason
import parkinglot.simulator.domain.connector.LicensePlateReader

class LicensePlateReaderImpl: LicensePlateReader {

    override suspend fun read(): Either<DenyEntryReason, String> {

        delay(500)

        // simulate the license plate reading process in a predictable way
        val plate = (1..5)
            .map { ('a'..'z').random(Random) }
            .joinToString("")

       return plate.right()
    }
}
