package parkinglot.simulator.connector.sensor.system.adapter

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
import parkinglot.simulator.domain.model.LicensePlate
import parkinglot.simulator.domain.model.ParkingSpotId
import parkinglot.simulator.domain.model.SensorEvent
import parkinglot.simulator.domain.model.SensorEvent.ParkingSpotOccupiedEvent
import parkinglot.simulator.domain.model.SensorEvent.ParkingSpotReleasedEvent
import parkinglot.simulator.domain.model.SensorEvent.VehicleEnteringEvent
import parkinglot.simulator.domain.validator.EventValidator
import kotlinx.coroutines.delay
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.time.Duration
import java.time.Instant

private class FixedProcessingTimeSensorEventHandler(
    private val processingTime: Long,
    private val processingStatusSensorEventRepository: ProcessingStatusSensorEventRepository
) : SensorEventHandler {
    override suspend fun handle(event: SensorEvent) {
        delay(processingTime)
        processingStatusSensorEventRepository.setProcessingStatusToCompleted(event, Instant.now())
    }
}

class SensorEventAdapterTest {
    private val event = VehicleEnteringEvent()
    private val publisher = EventPublisher()
    private val eventHandler = mockk<SensorEventHandler>()
    private val processingStatusSensorEventRepository = mockk<ProcessingStatusSensorEventRepository>(relaxed = true)
    private val meterRegistry = SimpleMeterRegistry()

    private fun sensorEventAdapter(
        eventSource: SensorEventSource = publisher,
        eventHandler: SensorEventHandler = this.eventHandler,
        eventValidator: EventValidator = mockk(relaxed = true),
        processingStatusSensorEventRepository: ProcessingStatusSensorEventRepository = this@SensorEventAdapterTest.processingStatusSensorEventRepository,
        meterRegistry: MeterRegistry = this.meterRegistry,
        earlierEventsPollIntervalMs: Long = 10,
        earlierEventsMaxPolls: Int = 10
    ) = SensorEventAdapter(eventSource, eventHandler, eventValidator, processingStatusSensorEventRepository, meterRegistry, earlierEventsPollIntervalMs, earlierEventsMaxPolls)

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
        // when
        val eventValidator = mockk<EventValidator> { every { isValid(any()) } returns true }
        coEvery { eventHandler.handle(any()) } just Runs
        every { processingStatusSensorEventRepository.isProcessing(event.eventId) } returns false
        val adapter = sensorEventAdapter(eventValidator = eventValidator)

        try {
            // then
            adapter.start()
            publisher.simulateEventEmissions(listOf(event))

            // expect
            coVerify(timeout = 1_000, exactly = 1) { eventHandler.handle(event) }
            verify { processingStatusSensorEventRepository.setProcessingStatusToInProgress(event, 1) }
        } finally {
            adapter.close()
        }
    }

    @Test
    fun `duplicate event is not handled and throws exception and stop program`() {
        // when
        val eventValidator = mockk<EventValidator> { every { isValid(any()) } returns true }
        coEvery { eventHandler.handle(any()) } just Runs
        every { processingStatusSensorEventRepository.isProcessing(event.eventId) } returnsMany listOf(false, true)
        val adapter = sensorEventAdapter(eventValidator = eventValidator)

        try {
            // then
            adapter.start()
            publisher.simulateEventEmissions(listOf(event, event))

            // expect
            coVerify(timeout = 1_000, exactly = 1) { eventHandler.handle(any()) }

            assertThrows<DuplicateEventException> {
                runBlocking { adapter.awaitFailure() }
            }
            await().until { !adapter.isRunning }
        } finally {
            adapter.close()
        }
    }

    @Test
    fun `failed event is retried 3 times and rethrow exception and stop program`() {
        // when
        val eventValidator = mockk<EventValidator> { every { isValid(any()) } returns true }
        coEvery { eventHandler.handle(event) } throws IllegalStateException("persistence unavailable")
        val adapter = sensorEventAdapter(eventValidator = eventValidator)

        try {
            // then
            adapter.start()
            publisher.simulateEventEmissions(listOf(event))

            // expect
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
        fun `event still invalid after waiting for earlier events to complete throws exception and stop program`() {
            // when
            val eventValidator = eventValidatorWithEarlierEventsValid(listOf(false, false))
            coEvery { eventHandler.handle(any()) } just Runs
            every { processingStatusSensorEventRepository.isProcessing(any()) } returns false
            every { processingStatusSensorEventRepository.isEarlierEventsCompleted(3) } returnsMany listOf(false, false, true)
            val adapter = sensorEventAdapter(eventValidator = eventValidator)

            try {
                // then
                adapter.start()
                publisher.simulateEventEmissions(listOf(earlierEvent1, earlierEvent2, event))

                // expect
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
            // when
            val eventValidator = eventValidatorWithEarlierEventsValid(listOf(false, true))
            coEvery { eventHandler.handle(any()) } just Runs
            every { processingStatusSensorEventRepository.isProcessing(any()) } returns false
            every { processingStatusSensorEventRepository.isEarlierEventsCompleted(3) } returnsMany listOf(false, false, true)
            val adapter = sensorEventAdapter(eventValidator = eventValidator)

            try {
                // then
                adapter.start()
                publisher.simulateEventEmissions(listOf(earlierEvent1, earlierEvent2, event))

                // expect
                verify(timeout = 1_000, exactly = 3) { processingStatusSensorEventRepository.isEarlierEventsCompleted(3) }
                verify(exactly = 2) { eventValidator.isValid(event) }
                assertTrue(adapter.isRunning)
            } finally {
                adapter.close()
            }
        }

        @Test
        fun `gives up waiting for earlier events to be completed (needed for event-validation) after a bounded number of polls and throws exception and program stops`() {
            // when
            val eventValidator = mockk<EventValidator> { every { isValid(any()) } returns false }
            every { processingStatusSensorEventRepository.isEarlierEventsCompleted(1) } returns false
            val adapter = sensorEventAdapter(
                eventValidator = eventValidator,
                earlierEventsMaxPolls = 3
            )

            try {
                // then
                adapter.start()
                publisher.simulateEventEmissions(listOf(event))

                // expect
                verify(timeout = 1_000, exactly = 3) { processingStatusSensorEventRepository.isEarlierEventsCompleted(1) }
                assertThrows<InvalidEventException> {
                    runBlocking { adapter.awaitFailure() }
                }
                await().until { !adapter.isRunning }
            } finally {
                adapter.close()
            }
        }

    @Nested
    inner class Concurrency {
        @Test
        fun `If the event handler does not launch an asynchronous task, the events is processed synchronously`() {
            // when
            val eventValidator = mockk<EventValidator> { every { isValid(any()) } returns true }

            val events = List(4) {
                ParkingSpotOccupiedEvent(
                    LicensePlate((1..10).map { ('A'..'Z').random() }.joinToString("")),
                    ParkingSpotId("${listOf("A", "B").random()}${(1..25).random()}")
                )
            }
            val processingTime = 200L
            val handler = FixedProcessingTimeSensorEventHandler(processingTime, processingStatusSensorEventRepository)

            val adapter = sensorEventAdapter(eventValidator = eventValidator, eventHandler = handler)

            try {
                // then
                adapter.start()
                publisher.simulateEventEmissions(events)

                val processedAtSlots = events.map { event ->
                    val slot = slot<Instant>()
                    verify(timeout = 2_000) { processingStatusSensorEventRepository.setProcessingStatusToCompleted(event, capture(slot)) }
                    slot.captured
                }

                // expect
                processedAtSlots.zipWithNext().forEach { (earlier, later) ->
                    assertTrue(Duration.between(earlier, later).toMillis() >= processingTime)
                }
            } finally {
                adapter.close()
            }
        }

        @Test
        fun `If the event handler launch handling of VehicleEnteringEvent as an asynchronous task, the task is handled concurrently with the other events`() {
            // when
            val eventValidator = mockk<EventValidator> { every { isValid(any()) } returns true }
            every { processingStatusSensorEventRepository.isProcessing(any()) } returns false

            val enteringEvent = VehicleEnteringEvent()
            val spotReleasedEvent = ParkingSpotReleasedEvent(LicensePlate("AB123CD123"), ParkingSpotId("A1"))
            val eventsInEmissionOrder = listOf(enteringEvent, spotReleasedEvent)

            var spotReleasedProcessed = false
            var enteringCompleted = false

            val vehicleEnteringBackgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            coEvery { eventHandler.handle(enteringEvent) } coAnswers {
                vehicleEnteringBackgroundScope.launch {
                    delay(500)
                    enteringCompleted = true
                }
            }
            coEvery { eventHandler.handle(spotReleasedEvent) } coAnswers {
                spotReleasedProcessed = true
            }

            val adapter = sensorEventAdapter(eventValidator = eventValidator)

            try {
                // then
                adapter.start()
                publisher.simulateEventEmissions(eventsInEmissionOrder)

                await().until { spotReleasedProcessed }

                // expect
                assertTrue(spotReleasedProcessed)
                assertFalse(enteringCompleted)

                await().until { enteringCompleted }
                coVerify(exactly = 1) { eventHandler.handle(enteringEvent) }
                coVerify(exactly = 1) { eventHandler.handle(spotReleasedEvent) }
            } finally {
                vehicleEnteringBackgroundScope.cancel()
                adapter.close()
            }
        }
    }
}
}
