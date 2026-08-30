package parkinglot.simulator.repository

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import parkinglot.simulator.domain.repository.VehicleTransitRepository
import parkinglot.simulator.repository.jpa.adapter.VehicleTransitEntityRepository
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
class VehicleTransitRepositoryImplVerifyTest {

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
    private lateinit var repository: VehicleTransitRepository

    @Autowired
    private lateinit var jpaRepository: VehicleTransitEntityRepository

    @Test
    fun `addVehicleInTransit adds the license plate to vehicleInTransit list`() {
        val licensePlate = "AB123CD"

        repository.addVehicleInTransit(licensePlate)

        assertTrue(jpaRepository.existsById(licensePlate))
    }

    @Test
    fun `addVehicleInTransit is idempotent and doesn't throw exception when inserting a licensePlate twice`() {
        val licensePlate = "IJ012KL"
        val before = jpaRepository.count()

        repository.addVehicleInTransit(licensePlate)
        repository.addVehicleInTransit(licensePlate)

        assertEquals(before + 1, repository.getNumberOfVehiclesInTransit())
        assertTrue(jpaRepository.existsById(licensePlate))
    }

    @Test
    fun `removeVehicleInTransit removes the license plate from vehicleInTransit list`() {
        val licensePlate = "CD456EF"
        repository.addVehicleInTransit(licensePlate)

        repository.removeVehicleInTransit(licensePlate)

        assertFalse(jpaRepository.existsById(licensePlate))
    }

    @Test
    fun `getNumberOfVehiclesInTransit returns current license plate count`() {
        val before = jpaRepository.count()

        repository.addVehicleInTransit("EF789GH")

        assertEquals(before + 1, repository.getNumberOfVehiclesInTransit())
    }
}
