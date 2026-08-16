package parkinglot.simulator.domain.connector

import arrow.core.Either
import parkinglot.simulator.domain.model.DenyEntryReason
import parkinglot.simulator.domain.model.VehicleRegistrationLocation

interface PaymentStatusChecker {
    suspend fun isPaymentComplete(position: VehicleRegistrationLocation): Either<DenyEntryReason, Boolean>
}
