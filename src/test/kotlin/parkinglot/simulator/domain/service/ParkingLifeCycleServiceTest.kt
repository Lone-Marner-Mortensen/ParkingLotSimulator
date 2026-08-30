package parkinglot.simulator.domain.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import parkinglot.simulator.domain.repository.ParkingSpotRepository
import parkinglot.simulator.domain.repository.VehicleTransitRepository
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParkingLifeCycleServiceTest {
    private val parkingSpotRepository = mockk<ParkingSpotRepository>(relaxed = true)
    private val vehicleTransitRepository = mockk<VehicleTransitRepository>(relaxed = true)
    private val service = ParkingLifeCycleService(
        parkingSpotRepository,
        vehicleTransitRepository
    )
    private val licensePlate = "AB123CD"
    private val spotId = "A1"

    @Nested
    inner class ReserveIfCapacityAvailable {
        @Test
        fun `reserves a spot when capacity is available`() = runTest {
            every { parkingSpotRepository.getFreeParkingSpots() } returns listOf(spotId)
            every { vehicleTransitRepository.getNumberOfVehiclesInTransit() } returns 0L

            val reserved = service.reserveIfCapacityAvailable(licensePlate)

            assertTrue(reserved)
            verify { vehicleTransitRepository.addVehicleInTransit(licensePlate) }
        }

        @Test
        fun `does not reserve a spot when no capacity is available`() = runTest {
            every { parkingSpotRepository.getFreeParkingSpots() } returns listOf(spotId)
            every { vehicleTransitRepository.getNumberOfVehiclesInTransit() } returns 1L

            val reserved = service.reserveIfCapacityAvailable(licensePlate)

            assertFalse(reserved)
            verify(exactly = 0) { vehicleTransitRepository.addVehicleInTransit(any()) }
        }
    }

    @Test
    fun `occupyParkingSpot occupies the spot and removes the vehicle from transit`() {
        service.occupyParkingSpot(licensePlate, spotId)

        verify { parkingSpotRepository.occupyParkingSpot(licensePlate, spotId) }
        verify { vehicleTransitRepository.removeVehicleInTransit(licensePlate) }
    }

    @Test
    fun `releaseParkingSpot releases the spot and removes the vehicle from transit`() {
        service.releaseParkingSpot(licensePlate, spotId)

        verify { parkingSpotRepository.releaseParkingSpot(licensePlate, spotId) }
        verify { vehicleTransitRepository.removeVehicleInTransit(licensePlate) }
    }
}
