package parkinglot.simulator.connector.sensor.system.adapter

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import parkinglot.simulator.connector.sensor.system.EventPublisher
import parkinglot.simulator.domain.connector.SensorEventHandler
import parkinglot.simulator.domain.model.SensorEvent
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SensorEventAdapterLifecycleTest {
    @Test
    fun `start is idempotent and stop clears running state`() {
        val adapter = SensorEventAdapter(
            EventPublisher(),
            mockk<SensorEventHandler>(),
            mockk<TreatmentStatusSensorEventRepository>(),
            SimpleMeterRegistry()
        )

        adapter.start()
        adapter.start()
        assertTrue(adapter.isRunning)

        adapter.stop()
        assertFalse(adapter.isRunning)
        adapter.close()
    }

    @Test
    fun `duplicate event is not handled`() {
        val event = SensorEvent.VehicleEnteringEvent()
        val publisher = EventPublisher()
        val eventHandler = mockk<SensorEventHandler>()
        val processedEvents = mockk<TreatmentStatusSensorEventRepository>(relaxed = true)
        val adapter = SensorEventAdapter(
            publisher,
            eventHandler,
            processedEvents,
            SimpleMeterRegistry()
        )

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
    fun `failed event is retried three times and released`() {
        val event = SensorEvent.VehicleEnteringEvent()
        val publisher = EventPublisher()
        val eventHandler = mockk<SensorEventHandler>()
        val processedEvents = mockk<TreatmentStatusSensorEventRepository>(relaxed = true)
        every { processedEvents.underTreatment(event) } returns false
        coEvery { eventHandler.handle(event) } throws IllegalStateException("persistence unavailable")
        val consumer = SensorEventAdapter(
            publisher,
            eventHandler,
            processedEvents,
            SimpleMeterRegistry()
        )

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
