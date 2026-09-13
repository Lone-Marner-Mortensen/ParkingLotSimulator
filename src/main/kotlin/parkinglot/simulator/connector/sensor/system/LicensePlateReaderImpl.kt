package parkinglot.simulator.connector.sensor.system

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.delay
import org.springframework.stereotype.Component
import kotlin.random.Random
import parkinglot.simulator.domain.model.DenyEntryReason
import parkinglot.simulator.domain.connector.LicensePlateReader
import parkinglot.simulator.domain.model.LicensePlate
import kotlin.time.Duration.Companion.milliseconds

@Component
class LicensePlateReaderImpl: LicensePlateReader {

    override suspend fun read(): Either<DenyEntryReason, LicensePlate> {

        delay(500.milliseconds)

        if (Random.nextDouble() < 0.005) {
            return DenyEntryReason.TECHNICAL_FAILURE.left()
        }

        if (Random.nextDouble() < 0.02) {
            return DenyEntryReason.LICENSE_PLATE_NOT_READABLE.left()
        }

        val plate = LicensePlate.GENERATED_PREFIX + (1..6)
            .map { ('a'..'z').random(Random) }
            .joinToString("")

        return LicensePlate(plate).right()
    }
}
