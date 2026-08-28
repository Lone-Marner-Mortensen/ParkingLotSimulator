package parkinglot.simulator.domain.connector

import arrow.core.Either
import parkinglot.simulator.domain.model.DenyEntryReason

interface PaymentStatusChecker {
    suspend fun isPaymentComplete(): Either<DenyEntryReason, Boolean>
}
