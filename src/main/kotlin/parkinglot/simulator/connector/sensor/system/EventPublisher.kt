package parkinglot.simulator.connector.sensor.system

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import parkinglot.simulator.domain.model.SensorEvent

class EventPublisher(
    private val scope: CoroutineScope
) {
    private val events = MutableSharedFlow<SensorEvent>()

    fun observeEvents(): Flow<SensorEvent> = events

    fun simulateEventEmissions(eventsToEmit: List<SensorEvent>) {
        scope.launch {
            eventsToEmit.forEach { event -> events.emit(event) }
        }
    }
}
