package parkinglot.simulator.connector.sensor.system.adapter

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import parkinglot.simulator.connector.sensor.system.EventPublisher
import parkinglot.simulator.domain.connector.SensorEventHandler
import parkinglot.simulator.domain.connector.SensorEventSource
import parkinglot.simulator.domain.model.SensorEvent
import parkinglot.simulator.domain.validator.EventValidator
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SensorEventAdapterTest {
    private val event = SensorEvent.VehicleEnteringEvent()
    private val publisher = EventPublisher()
    private val eventHandler = mockk<SensorEventHandler>()
    private val processedEvents = mockk<TreatmentStatusSensorEventRepository>(relaxed = true)
    private val meterRegistry = SimpleMeterRegistry()

    private fun sensorEventAdapter(
        eventSource: SensorEventSource = publisher,
        eventHandler: SensorEventHandler = this.eventHandler,
        eventValidator: EventValidator = mockk(relaxed = true),
        treatmentStatusRepository: TreatmentStatusSensorEventRepository = processedEvents,
        meterRegistry: MeterRegistry = this.meterRegistry
    ) = SensorEventAdapter(eventSource, eventHandler, eventValidator, treatmentStatusRepository, meterRegistry)

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
    fun `duplicate event is not handled`() {
        val eventValidator = mockk<EventValidator> { every { isValid(any()) } returns true }
        val adapter = sensorEventAdapter(eventValidator = eventValidator)

        try {
            adapter.start()
            publisher.simulateEventEmissions(listOf(event, event))

            verify(timeout = 1_000, exactly = 1) { processedEvents.underTreatment(event) }
            coVerify(exactly = 1) { eventHandler.handle(any()) }
        } finally {
            adapter.close()
        }
    }

    @Test
    fun `invalid event is not handled`() {
        val eventValidator = mockk<EventValidator> { every { isValid(event) } returns false }
        val adapter = sensorEventAdapter(eventValidator = eventValidator)

        try {
            adapter.start()
            publisher.simulateEventEmissions(listOf(event))

            verify(timeout = 1_000, exactly = 1) { eventValidator.isValid(event) }
            verify(exactly = 0) { processedEvents.markAsUnderTreatment(event) }
            coVerify(exactly = 0) { eventHandler.handle(any()) }
        } finally {
            adapter.close()
        }
    }

    @Test
    fun `failed event is retried three times and released`() {
        val eventValidator = mockk<EventValidator> { every { isValid(any()) } returns true }
        every { processedEvents.underTreatment(event) } returns false
        coEvery { eventHandler.handle(event) } throws IllegalStateException("persistence unavailable")
        val consumer = sensorEventAdapter(eventValidator = eventValidator)

        try {
            consumer.start()
            publisher.simulateEventEmissions(listOf(event))

            verify(timeout = 2_000, exactly = 1) { processedEvents.markAsUnderTreatment(event) }
            verify(timeout = 2_000, exactly = 1) { processedEvents.unmarkAsUnderTreatment(event) }
            coVerify(exactly = 3) { eventHandler.handle(event) }
        } finally {
            consumer.close()
        }
    }
}
