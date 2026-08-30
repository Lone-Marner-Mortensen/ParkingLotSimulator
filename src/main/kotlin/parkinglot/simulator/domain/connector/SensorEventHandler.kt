package parkinglot.simulator.domain.connector

import parkinglot.simulator.domain.model.SensorEvent

interface SensorEventHandler {
    suspend fun handle(event: SensorEvent)
}
