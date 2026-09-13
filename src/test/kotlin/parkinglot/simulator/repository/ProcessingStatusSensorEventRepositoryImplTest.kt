package parkinglot.simulator.repository

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
import parkinglot.simulator.domain.repository.ProcessingStatusSensorEventRepository
import parkinglot.simulator.repository.jpa.adapter.ProcessingStatusSensorEventEntityRepository
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("test")
class ProcessingStatusSensorEventRepositoryImplTest {

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
    inner class IsProcessing {
        @Test
        fun `is false when no event with the given event-id exists`() {
            assertFalse(repository.isProcessing(eventId = "unknown-event-id"))
        }

        @Test
        fun `is true when the event is processing`() {
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

    @Nested
    inner class AllEventsCompleted {
        @Test
        fun `is true when there are no events`() {
            assertTrue(repository.allEventsCompleted(emptyList()))
        }

        @Test
        fun `is true when every given event has been completed`() {
            val event1 = VehicleEnteringEvent()
            val event2 = VehicleEnteringEvent()
            repository.setProcessingStatusToInProgress(event1, sequenceNumber = 1)
            repository.setProcessingStatusToCompleted(event1, Instant.now())
            repository.setProcessingStatusToInProgress(event2, sequenceNumber = 2)
            repository.setProcessingStatusToCompleted(event2, Instant.now())

            assertTrue(repository.allEventsCompleted(listOf(event1, event2)))
        }

        @Test
        fun `is false when a given event is still in progress`() {
            val completedEvent = VehicleEnteringEvent()
            repository.setProcessingStatusToInProgress(completedEvent, sequenceNumber = 1)
            repository.setProcessingStatusToCompleted(completedEvent, Instant.now())
            val inProgressEvent = VehicleEnteringEvent()
            repository.setProcessingStatusToInProgress(inProgressEvent, sequenceNumber = 2)

            assertFalse(repository.allEventsCompleted(listOf(completedEvent, inProgressEvent)))
        }
    }

    @Nested
    inner class GetMaxSequenceNumber {
        @Test
        fun `is 0 when there are no events`() {
            assertEquals(0, repository.getMaxSequenceNumber())
        }

        @Test
        fun `is the highest sequence number across in-progress and completed events`() {
            repository.setProcessingStatusToInProgress(VehicleEnteringEvent(), sequenceNumber = 5)
            val completedEvent = VehicleEnteringEvent()
            repository.setProcessingStatusToInProgress(completedEvent, sequenceNumber = 9)
            repository.setProcessingStatusToCompleted(completedEvent, Instant.now())

            assertEquals(9, repository.getMaxSequenceNumber())
        }
    }
}
