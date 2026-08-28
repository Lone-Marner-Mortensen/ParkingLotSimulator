package parkinglot.simulator.connector.payment.system

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.delay
import parkinglot.simulator.domain.connector.PaymentStatusChecker
import parkinglot.simulator.domain.model.DenyEntryReason

class PaymentStatusCheckerImpl: PaymentStatusChecker {

    override suspend fun isPaymentComplete(): Either<DenyEntryReason, Boolean> {
        delay(500)

        return true.right()
    }
}
