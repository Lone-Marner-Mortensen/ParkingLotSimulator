package parkinglot.simulator.connector.payment.system

import org.springframework.stereotype.Component

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.delay
import kotlin.random.Random
import parkinglot.simulator.domain.connector.PaymentStatusChecker
import parkinglot.simulator.domain.model.DenyEntryReason

@Component
class PaymentStatusCheckerImpl: PaymentStatusChecker {

    override suspend fun isPaymentComplete(): Either<DenyEntryReason, Boolean> {
        delay(500)

        if (Random.nextDouble() < 0.005) {
            return DenyEntryReason.TECHNICAL_FAILURE.left()
        }

        if (Random.nextDouble() < 0.02) {
            return DenyEntryReason.PAYMENT_NOT_ACCEPTED.left()
        }

        return true.right()
    }
}
