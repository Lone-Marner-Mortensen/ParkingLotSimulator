package parkinglot.simulator.domain.connector

import kotlinx.coroutines.flow.Flow
import parkinglot.simulator.domain.model.SensorEvent

interface SensorEventSource {
    fun observeEvents(): Flow<SensorEvent>
}