package parkinglot.simulator.fakeservice

import arrow.core.Either
import parkinglot.simulator.domain.connector.PaymentStatusChecker
import parkinglot.simulator.domain.model.DenyEntryReason

object FakePaymentStatusChecker : PaymentStatusChecker {
    override suspend fun isPaymentComplete(): Either<DenyEntryReason, Boolean> = throw NotImplementedError()
}
