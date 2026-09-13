package parkinglot.simulator.connector.payment.system

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.delay
import kotlin.random.Random
import parkinglot.simulator.domain.connector.PaymentStatusChecker
import parkinglot.simulator.domain.model.DenyEntryReason
import kotlin.time.Duration.Companion.milliseconds

@Component
class PaymentStatusCheckerImpl(
    private val meterRegistry: MeterRegistry
) : PaymentStatusChecker {

    override suspend fun wasPaymentSuccessful(): Either<DenyEntryReason, Boolean> {
        delay(500.milliseconds)

        if (Random.nextDouble() < 0.005) {
            meterRegistry.counter("parking.payment.checks", "outcome", "technical_failure").increment()
            return DenyEntryReason.TECHNICAL_FAILURE.left()
        }

        if (Random.nextDouble() < 0.02) {
            meterRegistry.counter("parking.payment.checks", "outcome", "not_accepted").increment()
            return DenyEntryReason.PAYMENT_NOT_ACCEPTED.left()
        }

        meterRegistry.counter("parking.payment.checks", "outcome", "successful").increment()
        return true.right()
    }
}
