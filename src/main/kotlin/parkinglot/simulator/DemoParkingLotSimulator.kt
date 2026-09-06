package parkinglot.simulator

import arrow.core.tail
import io.micrometer.core.instrument.MeterRegistry
import kotlin.system.exitProcess
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import parkinglot.simulator.connector.sensor.system.EventPublisher
import parkinglot.simulator.connector.sensor.system.adapter.ProcessingStatusSensorEventRepository
import parkinglot.simulator.domain.model.builder.sensorEvents
import parkinglot.simulator.domain.model.builder.spots
import parkinglot.simulator.domain.model.LicensePlate
import org.awaitility.Awaitility.await
import parkinglot.simulator.domain.exception.DuplicateEventException
import parkinglot.simulator.domain.exception.InvalidEventException
import parkinglot.simulator.domain.repository.ParkingSpotRepository
import parkinglot.simulator.domain.repository.VehicleTransitRepository
import kotlin.time.Duration.Companion.minutes

@Component
@Profile("!test")
class DemoParkingLotSimulator(
    private val eventPublisher: EventPublisher,
    private val vehicleTransitRepository: VehicleTransitRepository,
    private val parkingSpotRepository: ParkingSpotRepository,
    private val processingStatusSensorEventRepository: ProcessingStatusSensorEventRepository,
    private val meterRegistry: MeterRegistry
) : CommandLineRunner {

    override fun run(vararg args: String) {
        try {
            val plateGroup1 = listOf(LicensePlate("LIPLA11111"), LicensePlate("LIPLA11222"), LicensePlate("LIPLA33333"))
            val plateGroup2 = listOf(LicensePlate("LIPLA44444"), LicensePlate("LIPLA55555"),
                LicensePlate("LIPLA66666"), LicensePlate("LIPLA77777"))

            val plateGroup3 = listOf(LicensePlate("LIPLA88888"), LicensePlate("LIPLA99999"), LicensePlate("LIPLA10101"))

            // Make sure events are valid. For example, if a car is occupying a parking spot, it must be in transit first.
            vehicleTransitRepository.removeAllVehiclesInTransit()
            parkingSpotRepository.releaseAllParkingSpots()
            (plateGroup1 + plateGroup2).forEach { vehicleTransitRepository.addVehicleInTransit(it.value) }
            plateGroup3.zip(spots("B", 1..3)).forEach { (plate, spot) -> parkingSpotRepository.occupyParkingSpot(plate.value, spot.value) }

            // Define events to simulate
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

            // Simulate events
            runBlocking {
                eventPublisher.simulateEventEmissions(events)
            }

            await().until { processingStatusSensorEventRepository.allEventsCompleted(events) }

            val demoSpots = spots("A", 19..25) + spots("B", 1..3)
            val occupiedSpots = demoSpots.filterNot { it.value in parkingSpotRepository.getFreeParkingSpots() }.toSet()
            logger.info("")
            logger.info("Taken spots: ${occupiedSpots.map { it.value }}")
            logger.info("Vehicles in transit: ${vehicleTransitRepository.getNumberOfVehiclesInTransit()}")
            logger.info("")

            exitProcess(0)
        }
        catch (exception: Exception) {
            logger.info("")
            when (exception) {
                is IllegalArgumentException, is DuplicateEventException, is InvalidEventException ->
                    logger.error("DemoParkingLotSimulator failed: {}", exception.message)
                else ->
                    logger.error("DemoParkingLotSimulator failed: {}", exception.message, exception)
            }
            logger.info("")

            exitProcess(1)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DemoParkingLotSimulator::class.java)
    }
}
