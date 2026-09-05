package parkinglot.simulator.connector.sensor.system

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import parkinglot.simulator.domain.model.SensorEvent.VehicleEnteringEvent

class EventPublisherTest {
    @Test
    fun `event emitted before subscription is retained`() = runTest {
        val publisher = EventPublisher()
        val event = VehicleEnteringEvent()

        publisher.simulateEventEmissions(listOf(event))

        assertEquals(event, publisher.observeEvents().first())
    }
}
