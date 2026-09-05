package parkinglot.simulator.domain.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import parkinglot.simulator.connector.sensor.system.adapter.ProcessingStatusSensorEventRepository
import parkinglot.simulator.domain.connector.ParkingGuardNotifier
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
import parkinglot.simulator.domain.model.SensorEvent.VehicleLeavingEvent

class ParkingLifeCycleServiceTest {
    private val parkingSpotRepository = mockk<ParkingSpotRepository>(relaxed = true)
    private val vehicleTransitRepository = mockk<VehicleTransitRepository>(relaxed = true)
    private val processingStatusSensorEventRepository = mockk<ProcessingStatusSensorEventRepository>(relaxed = true)
    private val parkingGuardNotifier = mockk<ParkingGuardNotifier>(relaxed = true)
    private val service = ParkingLifeCycleService(
        parkingSpotRepository,
        vehicleTransitRepository,
        processingStatusSensorEventRepository,
        parkingGuardNotifier
    )
    private val licensePlate = "AB123CD123"
    private val spotId = "A1"
    private val licensePlateValue = LicensePlate(licensePlate)
    private val spotIdValue = ParkingSpotId(spotId)

    @Nested
    inner class ReserveIfCapacityAvailable {
        @Test
        fun `reserves a spot when capacity is available`() = runTest {
            every { parkingSpotRepository.getFreeParkingSpots() } returns listOf(spotId)
            every { vehicleTransitRepository.getNumberOfVehiclesInTransit() } returns 0

            val reserved = service.reserveIfCapacityAvailable(licensePlate)

            assertTrue(reserved)
            verify { vehicleTransitRepository.addVehicleInTransit(licensePlate) }
        }

        @Test
        fun `does not reserve a spot when no capacity is available`() = runTest {
            every { parkingSpotRepository.getFreeParkingSpots() } returns listOf(spotId)
            every { vehicleTransitRepository.getNumberOfVehiclesInTransit() } returns 1

            val reserved = service.reserveIfCapacityAvailable(licensePlate)

            assertFalse(reserved)
            verify(exactly = 0) { vehicleTransitRepository.addVehicleInTransit(any()) }
        }
    }

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
}
