package parkinglot.simulator.connector.sensor.system

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import org.springframework.stereotype.Component
import parkinglot.simulator.domain.connector.SensorEventSource
import parkinglot.simulator.domain.model.LicensePlate
import parkinglot.simulator.domain.model.SensorEvent
import parkinglot.simulator.domain.model.SensorEvent.OverStayingEvent
import parkinglot.simulator.domain.model.SensorEvent.ParkingSpotOccupiedEvent
import parkinglot.simulator.domain.model.SensorEvent.ParkingSpotReleasedEvent
import parkinglot.simulator.domain.model.SensorEvent.VehicleEnteringEvent
import parkinglot.simulator.domain.model.SensorEvent.VehicleLeavingEvent

@Component
class EventPublisher : SensorEventSource {
    private val events = Channel<SensorEvent>(Channel.UNLIMITED)

    override fun observeEvents(): Flow<SensorEvent> = events.receiveAsFlow()

    fun simulateEventEmissions(eventsToEmit: List<SensorEvent>) {
        eventsToEmit.forEach { event ->
            require(!event.licensePlate().isReservedForGeneration()) {
                "Simulated event $event\n" +
                    "uses the license-plate prefix \"${LicensePlate.GENERATED_PREFIX}\", " +
                    "which is reserved for LicensePlateReaderImpl."
            }
            events.trySend(event).getOrThrow()
        }
    }

    private fun SensorEvent.licensePlate(): LicensePlate? = when (this) {
        is VehicleEnteringEvent -> null
        is ParkingSpotOccupiedEvent -> licensePlate
        is ParkingSpotReleasedEvent -> licensePlate
        is VehicleLeavingEvent -> licensePlate
        is OverStayingEvent -> licensePlate
    }

    private fun LicensePlate?.isReservedForGeneration(): Boolean =
        this?.value?.startsWith(LicensePlate.GENERATED_PREFIX) == true
}
