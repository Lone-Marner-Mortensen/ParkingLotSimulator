package parkinglot.simulator.domain.builder

import parkinglot.simulator.domain.model.ParkingSpotId
import parkinglot.simulator.domain.model.SensorEvent

fun spots(prefix: String, range: IntRange): List<ParkingSpotId> =
    range.map { ParkingSpotId("$prefix$it") }

fun sensorEvents(block: SensorEventsBuilder.() -> Unit): List<SensorEvent> =
    SensorEventsBuilder().apply(block).build()
