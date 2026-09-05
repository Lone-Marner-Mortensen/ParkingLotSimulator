package parkinglot.simulator.connector.sensor.system.adapter

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import parkinglot.simulator.connector.sensor.system.EventPublisher
import parkinglot.simulator.domain.connector.SensorEventHandler
import parkinglot.simulator.domain.connector.SensorEventSource
import parkinglot.simulator.domain.exception.DuplicateEventException
import parkinglot.simulator.domain.exception.InvalidEventException
import parkinglot.simulator.domain.model.SensorEvent.VehicleEnteringEvent
import parkinglot.simulator.domain.validator.EventValidator
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SensorEventAdapterTest {
    private val event = VehicleEnteringEvent()
    private val publisher = EventPublisher()
    private val eventHandler = mockk<SensorEventHandler>()
    private val earlierEventsCompletionTimeoutMs = 200
    private val processingStatusSensorEventRepository = mockk<ProcessingStatusSensorEventRepository>(relaxed = true)
    private val meterRegistry = SimpleMeterRegistry()

    private fun sensorEventAdapter(
        eventSource: SensorEventSource = publisher,
        eventHandler: SensorEventHandler = this.eventHandler,
        eventValidator: EventValidator = mockk(relaxed = true),
        processingStatusSensorEventRepository: ProcessingStatusSensorEventRepository = this@SensorEventAdapterTest.processingStatusSensorEventRepository,
        meterRegistry: MeterRegistry = this.meterRegistry
    ) = SensorEventAdapter(eventSource, eventHandler, eventValidator, processingStatusSensorEventRepository, meterRegistry, earlierEventsCompletionTimeoutMs)

    @Test
    fun `start is idempotent and stop clears running state`() {
        val adapter = sensorEventAdapter()

        adapter.start()
        adapter.start()
        assertTrue(adapter.isRunning)

        adapter.stop()
        assertFalse(adapter.isRunning)
        adapter.close()
    }

    @Test
    fun `valid non-duplicate event is marked as in-progress and calls eventHandler`() {
        val eventValidator = mockk<EventValidator> { every { isValid(any()) } returns true }
        coEvery { eventHandler.handle(any()) } just Runs
        every { processingStatusSensorEventRepository.isProcessing(event.eventId) } returns false
        val adapter = sensorEventAdapter(eventValidator = eventValidator)

        try {
            adapter.start()
            publisher.simulateEventEmissions(listOf(event))

            coVerify(timeout = 1_000, exactly = 1) { eventHandler.handle(event) }
            verify { processingStatusSensorEventRepository.setProcessingStatusToInProgress(event, 1) }
            assertTrue(adapter.isRunning)
        } finally {
            adapter.close()
        }
    }

    @Test
    fun `duplicate event is not handled and throws exception and stops`() {
        val eventValidator = mockk<EventValidator> { every { isValid(any()) } returns true }
        coEvery { eventHandler.handle(any()) } just Runs
        every { processingStatusSensorEventRepository.isProcessing(event.eventId) } returnsMany listOf(false, true)
        val adapter = sensorEventAdapter(eventValidator = eventValidator)

        try {
            adapter.start()
            publisher.simulateEventEmissions(listOf(event, event))

            coVerify(timeout = 1_000, exactly = 1) { eventHandler.handle(any()) }

            assertThrows<DuplicateEventException> {
                runBlocking { adapter.awaitFailure() }
            }
            await().until { !adapter.isRunning }
        } finally {
            adapter.close()
        }
    }

    @Nested
    inner class InvalidEvents {
        val earlierEvent1 = VehicleEnteringEvent()
        val earlierEvent2 = VehicleEnteringEvent()

        private fun eventValidatorWithEarlierEventsValid(currentEventsValidityResults: List<Boolean>) =
            mockk<EventValidator> {
                every { isValid(earlierEvent1) } returns true
                every { isValid(earlierEvent2) } returns true
                every { isValid(event) } returnsMany currentEventsValidityResults
            }

        @Test
        fun `event still invalid after waiting for earlier events to complete throws exception and stops`() {
            val eventValidator = eventValidatorWithEarlierEventsValid(listOf(false, false))
            coEvery { eventHandler.handle(any()) } just Runs
            every { processingStatusSensorEventRepository.isProcessing(any()) } returns false
            every { processingStatusSensorEventRepository.isEarlierEventsCompleted(3) } returnsMany listOf(false, false, true)
            val adapter = sensorEventAdapter(eventValidator = eventValidator)

            try {
                adapter.start()
                publisher.simulateEventEmissions(listOf(earlierEvent1, earlierEvent2, event))

                coVerify(timeout = 1_000) { eventHandler.handle(earlierEvent2) }
                verify(timeout = 1_000, exactly = 2) { eventValidator.isValid(event) }
                verify(exactly = 3) { processingStatusSensorEventRepository.isEarlierEventsCompleted(3) }
                coVerify(exactly = 0) { eventHandler.handle(event) }

                assertThrows<InvalidEventException> {
                    runBlocking { adapter.awaitFailure() }
                }
                await().until { !adapter.isRunning }
            } finally {
                adapter.close()
            }
        }

        @Test
        fun `invalid event becomes valid after waiting for earlier events to complete`() {
            val eventValidator = eventValidatorWithEarlierEventsValid(listOf(false, true))
            coEvery { eventHandler.handle(any()) } just Runs
            every { processingStatusSensorEventRepository.isProcessing(any()) } returns false
            every { processingStatusSensorEventRepository.isEarlierEventsCompleted(3) } returnsMany listOf(false, false, true)
            val adapter = sensorEventAdapter(eventValidator = eventValidator)

            try {
                adapter.start()
                publisher.simulateEventEmissions(listOf(earlierEvent1, earlierEvent2, event))

                coVerify(timeout = 1_000, exactly = 1) { eventHandler.handle(event) }
                verify(exactly = 3) { processingStatusSensorEventRepository.isEarlierEventsCompleted(3) }
                verify(exactly = 2) { eventValidator.isValid(event) }
                assertTrue(adapter.isRunning)
            } finally {
                adapter.close()
            }
        }
    }

    @Test
    fun `failed event is retried 3 times and rethrow exception and stops`() {
        val eventValidator = mockk<EventValidator> { every { isValid(any()) } returns true }
        coEvery { eventHandler.handle(event) } throws IllegalStateException("persistence unavailable")
        val adapter = sensorEventAdapter(eventValidator = eventValidator)

        try {
            adapter.start()
            publisher.simulateEventEmissions(listOf(event))

            val exception = assertThrows<IllegalStateException> {
                runBlocking { adapter.awaitFailure() }
            }
            assertEquals("persistence unavailable", exception.message)
            coVerify(exactly = 3) { eventHandler.handle(event) }
            await().until { !adapter.isRunning }
        } finally {
            adapter.close()
        }
    }
}
