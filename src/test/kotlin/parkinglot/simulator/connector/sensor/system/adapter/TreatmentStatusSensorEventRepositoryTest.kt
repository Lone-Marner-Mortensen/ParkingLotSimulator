package parkinglot.simulator.connector.sensor.system.adapter

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import parkinglot.simulator.domain.model.SensorEvent
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("test")
class TreatmentStatusSensorEventRepositoryTest {

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
    private lateinit var repository: TreatmentStatusSensorEventRepository

    @Autowired
    private lateinit var jpaRepository: TreatmentStatusSensorEventEntityRepository

    @Nested
    inner class UnderTreatment {
        @Test
        fun `is false for an event that has not been marked`() {
            val event = SensorEvent.VehicleEnteringEvent()

            assertFalse(repository.underTreatment(event))
        }

        @Test
        fun `is true for an event that has been marked`() {
            val event = SensorEvent.VehicleEnteringEvent()
            repository.markAsUnderTreatment(event)

            assertTrue(repository.underTreatment(event))
        }
    }

    @Nested
    inner class MarkAsUnderTreatment {
        @Test
        fun `adds the event-id to the list of events-in-treatment`() {
            val event = SensorEvent.VehicleEnteringEvent()

            repository.markAsUnderTreatment(event)

            assertTrue(jpaRepository.existsById(event.eventId))
            assertTrue(repository.underTreatment(event))
        }

        @Test
        fun `is idempotent and does not throw an exception when marking the same event twice`() {
            val event = SensorEvent.VehicleEnteringEvent()

            repository.markAsUnderTreatment(event)
            repository.markAsUnderTreatment(event)

            assertTrue(repository.underTreatment(event))
        }
    }

    @Test
    fun `unmarkAsUnderTreatment removes the event-Id from the list of events-in-treatment`() {
        val event = SensorEvent.VehicleEnteringEvent()
        repository.markAsUnderTreatment(event)

        repository.unmarkAsUnderTreatment(event)

        assertFalse(jpaRepository.existsById(event.eventId))
        assertFalse(repository.underTreatment(event))
    }
}
