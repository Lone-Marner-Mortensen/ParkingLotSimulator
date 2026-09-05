package parkinglot.simulator.connector.sensor.system.adapter

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import parkinglot.simulator.domain.model.SensorEvent.VehicleEnteringEvent
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("test")
class ProcessingStatusSensorEventRepositoryTest {

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
    private lateinit var repository: ProcessingStatusSensorEventRepository

    @Autowired
    private lateinit var jpaRepository: ProcessingStatusSensorEventEntityRepository

    @BeforeEach
    fun clearProcessingStatuses() {
        jpaRepository.deleteAll()
    }

    @Nested
    inner class SetProcessingStatusToInProgress {
        @Test
        fun `marks the event as in progress with the given sequence number`() {
            val event = VehicleEnteringEvent()

            repository.setProcessingStatusToInProgress(event, sequenceNumber = 17)

            val entity = jpaRepository.findById(event.eventId).orElse(null)
            assertEquals(17, entity.sequenceNumber)
            assertEquals(true, entity.isProcessing)
            assertNull(entity.processedAt)
        }
    }

    @Nested
    inner class SetProcessingStatusToCompleted {
        @Test
        fun `marks the event as completed with the given processed-at timestamp and keeps the sequence number`() {
            val event = VehicleEnteringEvent()
            repository.setProcessingStatusToInProgress(event, sequenceNumber = 17)
            val processedAt = Instant.now()

            repository.setProcessingStatusToCompleted(event, processedAt)

            val entity = jpaRepository.findById(event.eventId).orElse(null)
            assertEquals(17, entity.sequenceNumber)
            assertEquals(false, entity.isProcessing)
            assertEquals(processedAt, entity.processedAt)
        }
    }

    @Nested
    inner class IsEarlierEventsCompleted {
        @Test
        fun `is true when there are no earlier sequence numbers`() {
            assertTrue(repository.isEarlierEventsCompleted(sequenceNumber = 1))
        }

        @Test
        fun `is true when every earlier sequence number has a completed event`() {
            (1..9).forEach { earlierSequenceNumber ->
                val earlierEvent = VehicleEnteringEvent()
                repository.setProcessingStatusToInProgress(earlierEvent, earlierSequenceNumber)
                repository.setProcessingStatusToCompleted(earlierEvent, Instant.now())
            }

            assertTrue(repository.isEarlierEventsCompleted(sequenceNumber = 10))
        }

        @Test
        fun `is false when an earlier sequence has no event attached to it`() {
            (1..8).forEach { earlierSequenceNumber ->
                val earlierEvent = VehicleEnteringEvent()
                repository.setProcessingStatusToInProgress(earlierEvent, earlierSequenceNumber)
                repository.setProcessingStatusToCompleted(earlierEvent, Instant.now())
            }
            // Sequence number 9 is never marked as completed

            assertFalse(repository.isEarlierEventsCompleted(sequenceNumber = 10))
        }

        @Test
        fun `is false when an earlier event is not completed`() {
            (1..8).forEach { earlierSequenceNumber ->
                val earlierEvent = VehicleEnteringEvent()
                repository.setProcessingStatusToInProgress(earlierEvent, earlierSequenceNumber)
                repository.setProcessingStatusToCompleted(earlierEvent, Instant.now())
            }
            val stillProcessingEvent = VehicleEnteringEvent()
            repository.setProcessingStatusToInProgress(stillProcessingEvent, sequenceNumber = 9)

            assertFalse(repository.isEarlierEventsCompleted(sequenceNumber = 10))
        }
    }

    @Nested
    inner class IsProcessing {
        @Test
        fun `is false when no event with the given event-id exists`() {
            assertFalse(repository.isProcessing(eventId = "unknown-event-id"))
        }

        @Test
        fun `is true when the event is in progress`() {
            val event = VehicleEnteringEvent()
            repository.setProcessingStatusToInProgress(event, sequenceNumber = 1)

            assertTrue(repository.isProcessing(event.eventId))
        }

        @Test
        fun `is false when the event has been completed`() {
            val event = VehicleEnteringEvent()
            repository.setProcessingStatusToInProgress(event, sequenceNumber = 1)
            repository.setProcessingStatusToCompleted(event, Instant.now())

            assertFalse(repository.isProcessing(event.eventId))
        }
    }
}
