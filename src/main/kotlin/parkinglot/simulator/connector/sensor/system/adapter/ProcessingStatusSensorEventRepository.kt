package parkinglot.simulator.connector.sensor.system.adapter

import org.springframework.stereotype.Component
import parkinglot.simulator.domain.model.SensorEvent
import java.time.Instant

@Component
class ProcessingStatusSensorEventRepository(
    private val jpaRepository: ProcessingStatusSensorEventEntityRepository
) {
    fun setProcessingStatusToInProgress(event: SensorEvent, sequenceNumber: Int) {
        jpaRepository.saveAndFlush(
            ProcessingStatusSensorEventEntity(
                eventId = event.eventId,
                sequenceNumber = sequenceNumber,
                isProcessing = true,
                processedAt = null
            )
        )
    }

    fun setProcessingStatusToCompleted(event: SensorEvent, processedAt: Instant) {
        val sequenceNumber = jpaRepository.findById(event.eventId).orElse(null)?.sequenceNumber
        jpaRepository.saveAndFlush(
            ProcessingStatusSensorEventEntity(
                eventId = event.eventId,
                sequenceNumber = sequenceNumber,
                isProcessing = false,
                processedAt = processedAt
            )
        )
    }

    fun isEarlierEventsCompleted(sequenceNumber: Int): Boolean {
        val completedEarlierEvents = jpaRepository.countBySequenceNumberLessThanAndProcessedAtIsNotNull(sequenceNumber)
        return completedEarlierEvents == (sequenceNumber - 1).toLong()
    }

    fun isProcessing(eventId: String): Boolean =
        jpaRepository.findById(eventId).orElse(null)?.isProcessing == true

    fun allEventsCompleted(events: List<SensorEvent>): Boolean =
        !jpaRepository.existsByEventIdInAndProcessedAtIsNull(events.map { it.eventId })
}
