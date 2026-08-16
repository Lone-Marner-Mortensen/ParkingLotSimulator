package parkinglot.simulator.connector.sensor.system

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.delay
import parkinglot.simulator.domain.model.DenyEntryReason
import parkinglot.simulator.domain.model.VehicleRegistrationLocation
import parkinglot.simulator.domain.connector.LicensePlateReader

class LicensePlateReaderImpl: LicensePlateReader {

    override suspend fun read(position: VehicleRegistrationLocation): Either<DenyEntryReason, String> {
        delay(500)
        // ToDo: Make output random with 95% chance of returning a valid license plate and 5% chance of returning null
        val plate: String? = "fdfgdfgd"
        return plate?.right() ?: DenyEntryReason.LICENSE_PLATE_NOT_READABLE.left()
    }
}
