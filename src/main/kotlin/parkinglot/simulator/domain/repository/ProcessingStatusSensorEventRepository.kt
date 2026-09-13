package parkinglot.simulator.domain.repository

import parkinglot.simulator.domain.model.SensorEvent
import java.time.Instant

interface ProcessingStatusSensorEventRepository {
    fun setProcessingStatusToInProgress(event: SensorEvent, sequenceNumber: Int)
    fun setProcessingStatusToCompleted(event: SensorEvent, processedAt: Instant)
    fun isProcessing(eventId: String): Boolean
    fun allEventsCompleted(events: List<SensorEvent>): Boolean
    fun getMaxSequenceNumber(): Int
}
