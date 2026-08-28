package parkinglot.simulator.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import parkinglot.simulator.connector.sensor.system.EventPublisher
import parkinglot.simulator.domain.model.LicensePlate
import parkinglot.simulator.domain.model.ParkingSpotId
import parkinglot.simulator.domain.model.SensorEvent
import parkinglot.simulator.domain.repository.ParkingSpotRepository
import parkinglot.simulator.domain.repository.VehicleTransitRepository
import parkinglot.simulator.fakeservice.FakeLicensePlateReader
import parkinglot.simulator.fakeservice.FakeParkingGuardNotifier
import parkinglot.simulator.fakeservice.FakeParkingSpotRepository
import parkinglot.simulator.fakeservice.FakePaymentStatusChecker
import parkinglot.simulator.fakeservice.FakeVehicleSizeEstimator
import kotlin.test.assertEquals

class ParkingControllerEventHandlingTest {

    private class TestScope {
        val licensePlate = LicensePlate("AB123CD")
        val spotId = ParkingSpotId("A1")
        val vehicleTransitRepository = FakeVehicleTransitRepository()
        val parkingSpotRepository = FakeParkingSpotRepository()
    }

    fun controller(
        scope: CoroutineScope,
        eventPublisher: EventPublisher,
        vehicleTransitRepository: VehicleTransitRepository,
        parkingSpotRepository: ParkingSpotRepository
    ): ParkingController {
        return ParkingController(
            eventPublisher = eventPublisher,
            licensePlateReader = FakeLicensePlateReader,
            vehicleSizeEstimator = FakeVehicleSizeEstimator,
            paymentStatusChecker = FakePaymentStatusChecker,
            parkingGuardNotifier = FakeParkingGuardNotifier(),
            vehicleTransitRepository = vehicleTransitRepository,
            parkingSpotRepository = parkingSpotRepository,
            coroutineScope = scope
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `ParkingSpotOccupiedEvent occupies the parking spot and removes the vehicle from transit`() = runTest {
        with(TestScope()) {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val eventPublisher = EventPublisher(scope)
            val controller = controller(scope, eventPublisher, vehicleTransitRepository, parkingSpotRepository)
            vehicleTransitRepository.addVehicleInTransit(licensePlate.value)

            controller.startSystem()
            eventPublisher.simulateEventEmissions(listOf(SensorEvent.ParkingSpotOccupiedEvent(licensePlate, spotId)))

            assertEquals(listOf(Pair(licensePlate, spotId)), parkingSpotRepository.occupiedSpots)
            assertEquals(0, vehicleTransitRepository.getNumberOfVehiclesInTransit())
        }
    }


    private class FakeVehicleTransitRepository : VehicleTransitRepository {
        private val vehiclesInTransit = mutableSetOf<String>()
        override fun addVehicleInTransit(licensePlate: String) {
            vehiclesInTransit.add(licensePlate)
        }

        override fun removeVehicleInTransit(licensePlate: String) {
            vehiclesInTransit.remove(licensePlate)
        }

        override fun getNumberOfVehiclesInTransit(): Int = vehiclesInTransit.size
    }
}

