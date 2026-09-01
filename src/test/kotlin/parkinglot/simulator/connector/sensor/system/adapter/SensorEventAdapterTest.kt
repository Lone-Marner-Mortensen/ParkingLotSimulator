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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
    private val treatmentStatusRepository = mockk<TreatmentStatusSensorEventRepository>(relaxed = true)
    private val meterRegistry = SimpleMeterRegistry()

    private fun sensorEventAdapter(
        eventSource: SensorEventSource = publisher,
        eventHandler: SensorEventHandler = this.eventHandler,
        eventValidator: EventValidator = mockk(relaxed = true),
        treatmentStatusRepository: TreatmentStatusSensorEventRepository = this@SensorEventAdapterTest.treatmentStatusRepository,
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
    fun `duplicate event is not handled and throws exception and stops`() {
        val eventValidator = mockk<EventValidator> { every { isValid(any()) } returns true }
        coEvery { eventHandler.handle(any()) } just Runs
        every { treatmentStatusRepository.underTreatment(event) } returnsMany listOf(false, true)
        val adapter = sensorEventAdapter(eventValidator = eventValidator)

        try {
            adapter.start()
            publisher.simulateEventEmissions(listOf(event, event))

            coVerify(timeout = 1_000, exactly = 1) { eventHandler.handle(any()) }
            // The sensorEventAdapter/consumer is stopped because an exception is thrown
            await().until { !adapter.isRunning }
        } finally {
            adapter.close()
        }
    }

    @Test
    fun `invalid event is not handled and throws exception and stops`() {
        val eventValidator = mockk<EventValidator> { every { isValid(event) } returns false }
        val adapter = sensorEventAdapter(eventValidator = eventValidator)

        try {
            adapter.start()
            publisher.simulateEventEmissions(listOf(event))

            verify(timeout = 1_000, exactly = 1) { eventValidator.isValid(event) }
            coVerify(exactly = 0) { eventHandler.handle(any()) }
            // The sensorEventAdapter/consumer is stopped because an exception is thrown
            await().until { !adapter.isRunning }
        } finally {
            adapter.close()
        }
    }

    @Test
    fun `failed event is retried three times then unmarked as under-treatment and rethrow exception and stops`() {
        val eventValidator = mockk<EventValidator> { every { isValid(any()) } returns true }
        every { treatmentStatusRepository.underTreatment(event) } returns false
        coEvery { eventHandler.handle(event) } throws IllegalStateException("persistence unavailable")
        val adapter = sensorEventAdapter(eventValidator = eventValidator)

        try {
            adapter.start()
            publisher.simulateEventEmissions(listOf(event))

            verify(timeout = 2_000, exactly = 1) { treatmentStatusRepository.unmarkAsUnderTreatment(event) }
            assertFalse(treatmentStatusRepository.underTreatment(event))
            coVerify(exactly = 3) { eventHandler.handle(event) }
            // The sensorEventAdapter/consumer is stopped because the exception is rethrown and not handled
            await().until { !adapter.isRunning }
        } finally {
            adapter.close()
        }
    }
}
