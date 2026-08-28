package parkinglot.simulator.repository

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import parkinglot.simulator.domain.model.LicensePlate
import parkinglot.simulator.domain.model.ParkingSpotId
import parkinglot.simulator.domain.repository.ParkingSpotRepository
import parkinglot.simulator.repository.jpa.adapter.ParkingSpotEntityRepository
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
class ParkingSpotRepositoryImplVerifyTest {

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

    @Autowired
    private lateinit var repository: ParkingSpotRepository

    @Autowired
    private lateinit var jpaRepository: ParkingSpotEntityRepository

    @Test
    fun `occupyParkingSpot sets licensePlate on the spot`() {
        val spotId = ParkingSpotId("A10")
        val licensePlate = LicensePlate("AB12345")

        repository.occupyParkingSpot(licensePlate, spotId)

        val after = jpaRepository.findById(spotId.value).orElseThrow()
        assertEquals(licensePlate.value, after.licensePlate)
    }

    @Test
    fun `releaseParkingSpot clears licensePlate on the spot`() {
        val spotId = ParkingSpotId("A11")
        val licensePlate = LicensePlate("CD67890")
        repository.occupyParkingSpot(licensePlate, spotId)

        repository.releaseParkingSpot(licensePlate, spotId)

        val after = jpaRepository.findById(spotId.value).orElseThrow()
        assertNull(after.licensePlate)
    }

    @Test
    fun `getFreeParkingSpots excludes occupied spots and includes released spots`() {
        val spotId = ParkingSpotId("A12")
        val licensePlate = LicensePlate("EF13579")

        repository.occupyParkingSpot(licensePlate, spotId)
        assertEquals(49, repository.getFreeParkingSpots().size)
        assertTrue(repository.getFreeParkingSpots().none { it == spotId.value })

        repository.releaseParkingSpot(licensePlate, spotId)
        assertEquals(50, repository.getFreeParkingSpots().size)
        assertTrue(repository.getFreeParkingSpots().any { it == spotId.value })
    }
}
