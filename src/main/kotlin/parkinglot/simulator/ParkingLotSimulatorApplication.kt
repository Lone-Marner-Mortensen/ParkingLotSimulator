package parkinglot.simulator

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import parkinglot.simulator.connector.parkingguard.notifier.system.ParkingGuardNotifierImpl
import parkinglot.simulator.connector.payment.system.PaymentStatusCheckerImpl
import parkinglot.simulator.connector.sensor.system.EventPublisher
import parkinglot.simulator.connector.sensor.system.LicensePlateReaderImpl
import parkinglot.simulator.connector.sensor.system.VehicleSizeEstimatorImpl
import parkinglot.simulator.domain.ParkingController
import parkinglot.simulator.domain.model.LicensePlate
import parkinglot.simulator.domain.model.ParkingSpotId
import parkinglot.simulator.domain.model.SensorEvent
import parkinglot.simulator.domain.repository.ParkingSpotRepository
import parkinglot.simulator.domain.repository.VehicleTransitRepository

@SpringBootApplication
@EnableScheduling
class ParkingLotSimulatorApplication

fun main(args: Array<String>) {
    val context = runApplication<ParkingLotSimulatorApplication>(*args)

    val vehicleTransitRepository = context.getBean(VehicleTransitRepository::class.java)
    val parkingSpotRepository = context.getBean(ParkingSpotRepository::class.java)

    val coroutineScope = CoroutineScope(Dispatchers.Default)
    val eventPublisher = EventPublisher(coroutineScope)
    val parkingController = ParkingController(
        eventPublisher = eventPublisher,
        licensePlateReader = LicensePlateReaderImpl(),
        vehicleSizeEstimator = VehicleSizeEstimatorImpl(),
        paymentStatusChecker = PaymentStatusCheckerImpl(),
        parkingGuardNotifier = ParkingGuardNotifierImpl(),
        vehicleTransitRepository = vehicleTransitRepository,
        parkingSpotRepository = parkingSpotRepository,
        coroutineScope = coroutineScope
    )

    runBlocking {
        parkingController.startSystem()

        val hardcodedEvents = listOf(
            SensorEvent.VehicleEnteringEvent,
            SensorEvent.ParkingSpotOccupiedEvent(LicensePlate("AB123CD"), ParkingSpotId("A1")),
            SensorEvent.ParkingSpotReleasedEvent(LicensePlate("AB123CD"), ParkingSpotId("A1"))
        )
        eventPublisher.simulateEventEmissions(hardcodedEvents)

        println("Number of cars who can enter: ${parkingController.getNumberOfVehiclesWhoCanEnter()}")
        println("Free parking spots: ${parkingController.getFreeParkingSpots()}")

        parkingController.stopSystem()
    }
}
