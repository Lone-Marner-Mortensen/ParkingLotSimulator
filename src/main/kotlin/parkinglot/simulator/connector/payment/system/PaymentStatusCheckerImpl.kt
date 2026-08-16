package parkinglot.simulator.connector.payment.system

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.delay
import parkinglot.simulator.domain.connector.PaymentStatusChecker
import parkinglot.simulator.domain.model.DenyEntryReason
import parkinglot.simulator.domain.model.VehicleRegistrationLocation

class PaymentStatusCheckerImpl: PaymentStatusChecker {

    override suspend fun isPaymentComplete(position: VehicleRegistrationLocation): Either<DenyEntryReason, Boolean> {

        delay(500)

        // ToDo: Make output random with 95% chance of returning true and 5% chance of returning false
        val paid = true
        return if (paid) true.right() else DenyEntryReason.PAYMENT_NOT_ACCEPTED.left()
    }
}
