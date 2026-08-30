package parkinglot.simulator.connector.sensor.system

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.delay
import org.springframework.stereotype.Component
import kotlin.random.Random
import parkinglot.simulator.domain.model.DenyEntryReason
import parkinglot.simulator.domain.connector.LicensePlateReader

@Component
class LicensePlateReaderImpl: LicensePlateReader {

    override suspend fun read(): Either<DenyEntryReason, String> {

        delay(500)

        if (Random.nextDouble() < 0.005) {
            return DenyEntryReason.TECHNICAL_FAILURE.left()
        }

        if (Random.nextDouble() < 0.02) {
            return DenyEntryReason.LICENSE_PLATE_NOT_READABLE.left()
        }

        val plate = (1..10)
            .map { ('a'..'z').random(Random) }
            .joinToString("")

        return plate.right()
    }
}
