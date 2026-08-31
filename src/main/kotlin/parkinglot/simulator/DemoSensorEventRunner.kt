package parkinglot.simulator

import arrow.core.tail
import kotlinx.coroutines.runBlocking
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import parkinglot.simulator.connector.sensor.system.EventPublisher
import parkinglot.simulator.domain.builder.sensorEvents
import parkinglot.simulator.domain.builder.spots
import parkinglot.simulator.domain.model.LicensePlate
import parkinglot.simulator.domain.repository.ParkingSpotRepository
import parkinglot.simulator.domain.repository.VehicleTransitRepository
import kotlin.time.Duration.Companion.minutes
import org.awaitility.Awaitility.await

// Publishes a scripted sequence of sensor events on startup to demonstrate a full vehicle lifecycle
@Component
class DemoSensorEventRunner(
    private val eventPublisher: EventPublisher,
    private val vehicleTransitRepository: VehicleTransitRepository,
    private val parkingSpotRepository: ParkingSpotRepository
) : CommandLineRunner {

    override fun run(vararg args: String) {
        val plateGroup1 = listOf(LicensePlate("MULTI11111"), LicensePlate("MULTI11222"), LicensePlate("MULTI33333"))
        val plateGroup2 = listOf(LicensePlate("MULTI44444"), LicensePlate("MULTI55555"),
            LicensePlate("MULTI66666"), LicensePlate("MULTI77777"))

        val plateGroup3 = listOf(LicensePlate("MULTI88888"), LicensePlate("MULTI99999"), LicensePlate("MULTI10101"))

        // Make sure events are valid
        vehicleTransitRepository.removeAllVehiclesInTransit()
        parkingSpotRepository.releaseAllParkingSpots()
        val demoPlates = plateGroup1 + plateGroup2
        val demoSpots = spots("A", 19..25)
        demoPlates.forEach { vehicleTransitRepository.addVehicleInTransit(it.value) }
        plateGroup3.zip(spots("B", 1..3)).forEach { (plate, spot) -> parkingSpotRepository.occupyParkingSpot(plate.value, spot.value) }

        val events = sensorEvents {
            vehicleEntering()
            vehicleEntering()
            vehicleEntering()

            occupied(plateGroup1, spots("A", 19..21))
            released(plateGroup1, spotIds = spots("A", 19..21))

            occupied(plateGroup2, spotIds = spots("A", 22..25))

            leaving(plateGroup3, spotIds = spots("B", 1..3))

            overstaying(plateGroup2.tail(), spotIds = spots("A", 23..25), duration = 25.minutes)
        }

        runBlocking {
            eventPublisher.simulateEventEmissions(events)
        }

        await().until {
            parkingSpotRepository.getFreeParkingSpots().size == 43 &&
                vehicleTransitRepository.getNumberOfVehiclesInTransit() == 6
        }
        val occupiedSpots = demoSpots.filterNot { it.value in parkingSpotRepository.getFreeParkingSpots() }.toSet()

        println("Taken spots: ${occupiedSpots.map { it.value }}")
        println("Vehicle in transition: ${vehicleTransitRepository.getNumberOfVehiclesInTransit()}")
    }
}
