package parkinglot.simulator.connector.parkingguard.notifier.system

import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

import parkinglot.simulator.domain.connector.ParkingGuardNotifier
import parkinglot.simulator.domain.model.DenyEntryReason
import kotlin.time.Duration


@Component
class ParkingGuardNotifierImpl(
    private val meterRegistry: MeterRegistry
) : ParkingGuardNotifier {

    override fun denyEntry(reason: DenyEntryReason) {
        // send reason to parking guard
        logger.info("Denying entry to vehicle due to reason: {}", reason)
        meterRegistry.counter("parking.guard.notifications", "type", "deny_entry").increment()
    }

    override fun vehicleHasOverStayed(licensePlate: String, parkingSpotId: String, duration: Duration) {
        // send information to parking guard about which vehicle has overstayed and for how long
        logger.info("Vehicle with license plate {} has overstayed in parking spot {} for duration {}", licensePlate, parkingSpotId, duration)
        meterRegistry.counter("parking.guard.notifications", "type", "overstay").increment()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ParkingGuardNotifierImpl::class.java)
    }
}
