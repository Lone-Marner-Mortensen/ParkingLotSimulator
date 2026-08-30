package parkinglot.simulator.connector.sensor.system

import org.springframework.stereotype.Component

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.delay
import kotlin.random.Random
import parkinglot.simulator.domain.model.DenyEntryReason
import parkinglot.simulator.domain.connector.VehicleSizeEstimator

@Component
class VehicleSizeEstimatorImpl: VehicleSizeEstimator {

    override suspend fun isVehicleTooBig(): Either<DenyEntryReason, Boolean> {

        delay(500)

        if (Random.nextDouble() < 0.005) {
            return DenyEntryReason.TECHNICAL_FAILURE.left()
        }

        if (Random.nextDouble() < 0.02) {
            return DenyEntryReason.VEHICLE_TOO_BIG.left()
        }

        return false.right()
    }
}
