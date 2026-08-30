package parkinglot.simulator.connector.sensor.system

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import org.springframework.stereotype.Component
import parkinglot.simulator.domain.connector.SensorEventSource
import parkinglot.simulator.domain.model.SensorEvent

@Component
class EventPublisher : SensorEventSource {
    private val events = Channel<SensorEvent>(Channel.UNLIMITED)

    override fun observeEvents(): Flow<SensorEvent> = events.receiveAsFlow()

    fun simulateEventEmissions(eventsToEmit: List<SensorEvent>) {
        eventsToEmit.forEach { event -> events.trySend(event).getOrThrow() }
    }
}
