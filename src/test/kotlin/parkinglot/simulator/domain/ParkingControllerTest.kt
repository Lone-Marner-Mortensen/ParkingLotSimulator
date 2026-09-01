package parkinglot.simulator.domain

import arrow.core.left
import arrow.core.right
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import parkinglot.simulator.domain.service.ParkingLifeCycleService
import parkinglot.simulator.domain.connector.LicensePlateReader
import parkinglot.simulator.domain.connector.ParkingGuardNotifier
import parkinglot.simulator.domain.connector.PaymentStatusChecker
import parkinglot.simulator.domain.connector.VehicleSizeEstimator
import parkinglot.simulator.domain.model.DenyEntryReason
import parkinglot.simulator.domain.model.LicensePlate
import parkinglot.simulator.domain.model.ParkingSpotId
import parkinglot.simulator.domain.model.SensorEvent
import parkinglot.simulator.domain.repository.VehicleTransitRepository
import kotlin.time.Duration.Companion.minutes

private data class DenialCase(
    val description: String,
    val expectedReason: DenyEntryReason,
    val stub: () -> Unit
)

class ParkingControllerTest {
    private val licensePlateReader = mockk<LicensePlateReader>()
    private val vehicleSizeEstimator = mockk<VehicleSizeEstimator>()
    private val paymentStatusChecker = mockk<PaymentStatusChecker>()
    private val parkingGuardNotifier = mockk<ParkingGuardNotifier>(relaxed = true)
    private val vehicleTransitRepository = mockk<VehicleTransitRepository>(relaxed = true)
    private val parkingLifecycleService = mockk<ParkingLifeCycleService>(relaxed = true)
    private val handler = ParkingController(
        licensePlateReader,
        vehicleSizeEstimator,
        paymentStatusChecker,
        parkingGuardNotifier,
        vehicleTransitRepository,
        parkingLifecycleService
    )
    private val licensePlate = "AB123CD123"
    private val spotId = "A1"
    private val licensePlateValue = LicensePlate(licensePlate)
    private val spotIdValue = ParkingSpotId(spotId)

    @Test
    fun `spot and transit events delegate to their use cases`() = runTest {
        handler.handle(SensorEvent.ParkingSpotOccupiedEvent(licensePlateValue, spotIdValue))
        handler.handle(SensorEvent.ParkingSpotReleasedEvent(licensePlateValue, spotIdValue))
        handler.handle(SensorEvent.VehicleLeavingEvent(licensePlateValue, spotIdValue))

        verify { parkingLifecycleService.occupyParkingSpot(licensePlate, spotId) }
        verify { parkingLifecycleService.releaseParkingSpot(licensePlate, spotId) }
        verify { vehicleTransitRepository.addVehicleInTransit(licensePlate) }
    }

    @Test
    fun `overstay event notifies guard`() = runTest {
        handler.handle(SensorEvent.OverStayingEvent(licensePlateValue, spotIdValue, 15.minutes))

        verify { parkingGuardNotifier.vehicleHasOverStayed(licensePlate, spotId, 15.minutes) }
    }

    @Nested
    inner class ParkingLotEntryDenial {
        @TestFactory
        fun `entry denied`() =
            listOf(
                DenialCase("vehicle is too big", DenyEntryReason.VEHICLE_TOO_BIG) {
                    coEvery { licensePlateReader.read() } returns licensePlate.right()
                    coEvery { vehicleSizeEstimator.isVehicleTooBig() } returns DenyEntryReason.VEHICLE_TOO_BIG.left()
                    coEvery { paymentStatusChecker.isPaymentComplete() } returns true.right()
                },
                DenialCase("payment fails", DenyEntryReason.PAYMENT_NOT_ACCEPTED) {
                    coEvery { licensePlateReader.read() } returns licensePlate.right()
                    coEvery { vehicleSizeEstimator.isVehicleTooBig() } returns false.right()
                    coEvery { paymentStatusChecker.isPaymentComplete() } returns DenyEntryReason.PAYMENT_NOT_ACCEPTED.left()
                },
                DenialCase("license plate reading fails", DenyEntryReason.LICENSE_PLATE_NOT_READABLE) {
                    coEvery { licensePlateReader.read() } returns DenyEntryReason.LICENSE_PLATE_NOT_READABLE.left()
                    coEvery { vehicleSizeEstimator.isVehicleTooBig() } returns false.right()
                    coEvery { paymentStatusChecker.isPaymentComplete() } returns true.right()
                }
            ).map { case ->
                dynamicTest("is reported if ${case.description}") {
                    runTest {
                        case.stub()

                        handler.handle(SensorEvent.VehicleEnteringEvent())

                        verify { parkingGuardNotifier.denyEntry(case.expectedReason) }
                        coVerify(exactly = 0) { parkingLifecycleService.reserveIfCapacityAvailable(any()) }
                    }
                }
            }
    }

    @Test
    fun `entry is denied after successful checks if capacity is unavailable`() = runTest {
        coEvery { licensePlateReader.read() } returns licensePlate.right()
        coEvery { vehicleSizeEstimator.isVehicleTooBig() } returns false.right()
        coEvery { paymentStatusChecker.isPaymentComplete() } returns true.right()
        coEvery { parkingLifecycleService.reserveIfCapacityAvailable(licensePlate) } returns false

        handler.handle(SensorEvent.VehicleEnteringEvent())

        verify { parkingGuardNotifier.denyEntry(DenyEntryReason.NO_AVAILABLE_PARKING_SPOTS) }
    }

    @Test
    fun `parking lot entry granted if vehicle is not too big and payment is complete and license plate is read successfully`() = runTest {
        coEvery { licensePlateReader.read() } returns licensePlate.right()
        coEvery { vehicleSizeEstimator.isVehicleTooBig() } returns false.right()
        coEvery { paymentStatusChecker.isPaymentComplete() } returns true.right()
        coEvery { parkingLifecycleService.reserveIfCapacityAvailable(licensePlate) } returns true

        handler.handle(SensorEvent.VehicleEnteringEvent())

        coVerify { parkingLifecycleService.reserveIfCapacityAvailable(licensePlate) }
    }
}
