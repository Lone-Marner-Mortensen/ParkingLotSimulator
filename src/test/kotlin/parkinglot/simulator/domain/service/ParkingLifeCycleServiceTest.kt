package parkinglot.simulator.domain.service

import arrow.core.left
import arrow.core.right
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import parkinglot.simulator.connector.sensor.system.adapter.ProcessingStatusSensorEventRepository
import parkinglot.simulator.domain.connector.LicensePlateReader
import parkinglot.simulator.domain.connector.ParkingGuardNotifier
import parkinglot.simulator.domain.connector.PaymentStatusChecker
import parkinglot.simulator.domain.connector.VehicleSizeEstimator
import parkinglot.simulator.domain.model.DenyEntryReason
import parkinglot.simulator.domain.model.LicensePlate
import parkinglot.simulator.domain.model.ParkingSpotId
import parkinglot.simulator.domain.model.SensorEvent
import parkinglot.simulator.domain.repository.ParkingSpotRepository
import parkinglot.simulator.domain.repository.VehicleTransitRepository
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import parkinglot.simulator.domain.model.SensorEvent.OverStayingEvent
import parkinglot.simulator.domain.model.SensorEvent.ParkingSpotOccupiedEvent
import parkinglot.simulator.domain.model.SensorEvent.ParkingSpotReleasedEvent
import parkinglot.simulator.domain.model.SensorEvent.VehicleEnteringEvent
import parkinglot.simulator.domain.model.SensorEvent.VehicleLeavingEvent

private data class DenialCase(
    val description: String,
    val expectedReason: DenyEntryReason,
    val stub: () -> Unit
)

class ParkingLifeCycleServiceTest {
    private val parkingSpotRepository = mockk<ParkingSpotRepository>(relaxed = true)
    private val vehicleTransitRepository = mockk<VehicleTransitRepository>(relaxed = true)
    private val processingStatusSensorEventRepository = mockk<ProcessingStatusSensorEventRepository>(relaxed = true)
    private val parkingGuardNotifier = mockk<ParkingGuardNotifier>(relaxed = true)
    private val licensePlateReader = mockk<LicensePlateReader>()
    private val vehicleSizeEstimator = mockk<VehicleSizeEstimator>()
    private val paymentStatusChecker = mockk<PaymentStatusChecker>()
    private val service = ParkingLifeCycleService(
        parkingSpotRepository,
        vehicleTransitRepository,
        processingStatusSensorEventRepository,
        parkingGuardNotifier,
        licensePlateReader,
        vehicleSizeEstimator,
        paymentStatusChecker
    )
    private val licensePlate = "AB123CD123"
    private val spotId = "A1"
    private val licensePlateValue = LicensePlate(licensePlate)
    private val spotIdValue = ParkingSpotId(spotId)

    @Test
    fun `occupyParkingSpot occupies the spot, removes the vehicle from transit and completes the event`() {
        val event = ParkingSpotOccupiedEvent(licensePlateValue, spotIdValue)

        service.occupyParkingSpot(event)

        verify { parkingSpotRepository.occupyParkingSpot(licensePlate, spotId) }
        verify { vehicleTransitRepository.removeVehicleInTransit(licensePlate) }
        verify { processingStatusSensorEventRepository.setProcessingStatusToCompleted(event, any()) }
    }

    @Test
    fun `releaseParkingSpot releases the spot, removes the vehicle from transit and completes the event`() {
        val event = ParkingSpotReleasedEvent(licensePlateValue, spotIdValue)

        service.releaseParkingSpot(event)

        verify { parkingSpotRepository.releaseParkingSpot(spotId) }
        verify { vehicleTransitRepository.removeVehicleInTransit(licensePlate) }
        verify { processingStatusSensorEventRepository.setProcessingStatusToCompleted(event, any()) }
    }

    @Test
    fun `markVehicleAsLeaving adds the vehicle to transit and completes the event`() {
        val event = VehicleLeavingEvent(licensePlateValue, spotIdValue)

        service.markVehicleAsLeaving(event)

        verify { vehicleTransitRepository.addVehicleInTransit(licensePlate) }
        verify { processingStatusSensorEventRepository.setProcessingStatusToCompleted(event, any()) }
    }

    @Test
    fun `overStaying notifies the parking guard and completes the event`() {
        val event = OverStayingEvent(licensePlateValue, spotIdValue, 15.minutes)

        service.overStaying(event)

        verify { parkingGuardNotifier.vehicleHasOverStayed(licensePlate, spotId, 15.minutes) }
        verify { processingStatusSensorEventRepository.setProcessingStatusToCompleted(event, any()) }
    }

    @Nested
    inner class HandleVehicleEntering {
        @Nested
        inner class ParkingLotEntryDenial {
            @TestFactory
            fun `entry denied`() =
                listOf(
                    DenialCase("vehicle is too big", DenyEntryReason.VEHICLE_TOO_BIG) {
                        coEvery { licensePlateReader.read() } returns licensePlate.right()
                        coEvery { vehicleSizeEstimator.isVehicleTooBig() } returns DenyEntryReason.VEHICLE_TOO_BIG.left()
                        coEvery { paymentStatusChecker.wasPaymentSuccessful() } returns true.right()
                    },
                    DenialCase("payment fails", DenyEntryReason.PAYMENT_NOT_ACCEPTED) {
                        coEvery { licensePlateReader.read() } returns licensePlate.right()
                        coEvery { vehicleSizeEstimator.isVehicleTooBig() } returns false.right()
                        coEvery { paymentStatusChecker.wasPaymentSuccessful() } returns DenyEntryReason.PAYMENT_NOT_ACCEPTED.left()
                    },
                    DenialCase("license plate reading fails", DenyEntryReason.LICENSE_PLATE_NOT_READABLE) {
                        coEvery { licensePlateReader.read() } returns DenyEntryReason.LICENSE_PLATE_NOT_READABLE.left()
                        coEvery { vehicleSizeEstimator.isVehicleTooBig() } returns false.right()
                        coEvery { paymentStatusChecker.wasPaymentSuccessful() } returns true.right()
                    },
                    DenialCase("no free spots", DenyEntryReason.NO_AVAILABLE_PARKING_SPOTS) {
                        coEvery { licensePlateReader.read() } returns licensePlate.right()
                        coEvery { vehicleSizeEstimator.isVehicleTooBig() } returns false.right()
                        coEvery { paymentStatusChecker.wasPaymentSuccessful() } returns true.right()
                        every { parkingSpotRepository.getFreeParkingSpots() } returns emptyList()
                        every { vehicleTransitRepository.getNumberOfVehiclesInTransit() } returns 0
                    }
                ).map { case ->
                    dynamicTest("is reported if ${case.description}") {
                        runTest {
                            case.stub()

                            service.handleVehicleEntering(VehicleEnteringEvent())

                            await().untilAsserted {
                                verify { parkingGuardNotifier.denyEntry(case.expectedReason) }
                            }
                        }
                    }
                }
        }

        @Test
        fun `handleVehicleEntering retries reserving capacity multiple times until it becomes available`() = runTest {
            // when
            coEvery { licensePlateReader.read() } returns licensePlate.right()
            coEvery { vehicleSizeEstimator.isVehicleTooBig() } returns false.right()
            coEvery { paymentStatusChecker.wasPaymentSuccessful() } returns true.right()
            // checking capacity
            every { parkingSpotRepository.getFreeParkingSpots() } returns listOf(spotId)
            every { vehicleTransitRepository.getNumberOfVehiclesInTransit() } returnsMany listOf(1, 1, 0)

            // then
            service.handleVehicleEntering(VehicleEnteringEvent())

            // expect
            verify(timeout = 2_000, exactly = 3) { vehicleTransitRepository.getNumberOfVehiclesInTransit() } // 3 checks for capacity
            verify { vehicleTransitRepository.addVehicleInTransit(licensePlate) } // capacity reserved
            verify(exactly = 0) { parkingGuardNotifier.denyEntry(DenyEntryReason.NO_AVAILABLE_PARKING_SPOTS) }
        }

        @Test
        fun `parking lot entry granted if vehicle is not too big, payment was successful, license plate was read successfully and spots available`() = runTest {
            // when
            coEvery { licensePlateReader.read() } returns licensePlate.right()
            coEvery { vehicleSizeEstimator.isVehicleTooBig() } returns false.right()
            coEvery { paymentStatusChecker.wasPaymentSuccessful() } returns true.right()
            every { parkingSpotRepository.getFreeParkingSpots() } returns listOf(spotId)
            every { vehicleTransitRepository.getNumberOfVehiclesInTransit() } returns 0

            // then
            service.handleVehicleEntering(VehicleEnteringEvent())

            // expect
            verify(timeout = 1_000) { vehicleTransitRepository.addVehicleInTransit(licensePlate) } // capacity reserved
        }

        @Test
        fun `vehicle entering event completes the event once handled`() = runTest {
            coEvery { licensePlateReader.read() } returns licensePlate.right()
            coEvery { vehicleSizeEstimator.isVehicleTooBig() } returns false.right()
            coEvery { paymentStatusChecker.wasPaymentSuccessful() } returns true.right()
            every { parkingSpotRepository.getFreeParkingSpots() } returns listOf(spotId)
            every { vehicleTransitRepository.getNumberOfVehiclesInTransit() } returns 0
            val event = VehicleEnteringEvent()

            service.handleVehicleEntering(event)

            await().untilAsserted {
                verify { processingStatusSensorEventRepository.setProcessingStatusToCompleted(event, any()) }
            }
        }
    }
}
