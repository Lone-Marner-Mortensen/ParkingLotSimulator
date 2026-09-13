package parkinglot.simulator.connector.sensor.system

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import parkinglot.simulator.domain.model.LicensePlate
import parkinglot.simulator.domain.model.ParkingSpotId
import parkinglot.simulator.domain.model.SensorEvent.ParkingSpotOccupiedEvent
import parkinglot.simulator.domain.model.SensorEvent.VehicleEnteringEvent

class EventPublisherTest {
    @Test
    fun `event emitted before subscription is retained`() = runTest {
        val publisher = EventPublisher()
        val event = VehicleEnteringEvent()

        publisher.simulateEventEmissions(listOf(event))

        assertEquals(event, publisher.observeEvents().first())
    }

    @Test
    fun `simulating an event with a license plate starting with GEN- throws an exception`() {
        val publisher = EventPublisher()
        val event = ParkingSpotOccupiedEvent(
            LicensePlate(LicensePlate.GENERATED_PREFIX + "abcdef"),
            ParkingSpotId("A1")
        )

        assertThrows<IllegalArgumentException> {
            publisher.simulateEventEmissions(listOf(event))
        }
    }
}
