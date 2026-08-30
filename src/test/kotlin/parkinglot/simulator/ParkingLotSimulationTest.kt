package parkinglot.simulator

import arrow.core.Either
import io.mockk.coEvery
import io.mockk.mockk
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import parkinglot.simulator.connector.sensor.system.EventPublisher
import parkinglot.simulator.domain.connector.LicensePlateReader
import parkinglot.simulator.domain.connector.ParkingGuardNotifier
import parkinglot.simulator.domain.connector.PaymentStatusChecker
import parkinglot.simulator.domain.connector.VehicleSizeEstimator
import parkinglot.simulator.domain.model.SensorEvent
import parkinglot.simulator.domain.repository.ParkingSpotRepository
import parkinglot.simulator.domain.repository.VehicleTransitRepository
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ParkingLotSimulationTest {

    @Autowired
    private lateinit var eventPublisher: EventPublisher

    @Autowired
    private lateinit var parkingSpotRepository: ParkingSpotRepository

    @Autowired
    private lateinit var vehicleTransitRepository: VehicleTransitRepository

    @Autowired
    private lateinit var licensePlateReader: LicensePlateReader

    @Autowired
    private lateinit var vehicleSizeEstimator: VehicleSizeEstimator

    @Autowired
    private lateinit var paymentStatusChecker: PaymentStatusChecker

    @TestConfiguration
    class MockConfig {
        @Bean
        @Primary
        fun licensePlateReader(): LicensePlateReader = mockk()

        @Bean
        @Primary
        fun vehicleSizeEstimator(): VehicleSizeEstimator = mockk()

        @Bean
        @Primary
        fun paymentStatusChecker(): PaymentStatusChecker = mockk()

        @Bean
        @Primary
        fun parkingGuardNotifier(): ParkingGuardNotifier = mockk(relaxed = true)
    }

    companion object {
        private val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16")
            .withDatabaseName("parkingLot")
            .withUsername("parkingLot")
            .withPassword("parkingLot")
            .apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun postgresProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }

        @JvmStatic
        @AfterAll
        fun tearDownContainer() {
            postgres.stop()
        }
    }

    @Test
    fun `full simulation of vehicle lifecycle`() {
        val plate = "WAW12345"
        val spotId = "A1"

        // 1. Vehicle Entering
        coEvery { licensePlateReader.read() } returns Either.Right(plate)
        coEvery { vehicleSizeEstimator.isVehicleTooBig() } returns Either.Right(false)
        coEvery { paymentStatusChecker.isPaymentComplete() } returns Either.Right(true)

        eventPublisher.simulateEventEmissions(listOf(SensorEvent.VehicleEnteringEvent()))

        await().until {
            vehicleTransitRepository.getNumberOfVehiclesInTransit() == 1L
        }

        // 2. Parking Spot Occupied
        eventPublisher.simulateEventEmissions(listOf(
            SensorEvent.ParkingSpotOccupiedEvent(plate, spotId)
        ))

        await().until {
            vehicleTransitRepository.getNumberOfVehiclesInTransit() == 0L &&
            parkingSpotRepository.getFreeParkingSpots().none { it == spotId }
        }

        // 3. Parking Spot Released
        eventPublisher.simulateEventEmissions(listOf(
            SensorEvent.ParkingSpotReleasedEvent(plate, spotId)
        ))

        await().until {
            parkingSpotRepository.getFreeParkingSpots().any { it == spotId }
        }

        // 4. Vehicle Leaving
        eventPublisher.simulateEventEmissions(listOf(
            SensorEvent.VehicleLeavingEvent(plate, spotId)
        ))

        await().until {
            vehicleTransitRepository.getNumberOfVehiclesInTransit() == 1L
        }
        
        // Final assertions
        assertEquals(1, vehicleTransitRepository.getNumberOfVehiclesInTransit())
        assertTrue(parkingSpotRepository.getFreeParkingSpots().any { it == spotId })
    }
}
