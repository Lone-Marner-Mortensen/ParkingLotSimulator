package parkinglot.simulator.repository

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import parkinglot.simulator.domain.model.SensorEvent
import parkinglot.simulator.domain.repository.ProcessingStatusSensorEventRepository
import parkinglot.simulator.repository.entity.ProcessingStatusSensorEventEntity
import parkinglot.simulator.repository.jpa.adapter.ProcessingStatusSensorEventEntityRepository
import java.time.Instant

@Component
class ProcessingStatusSensorEventRepositoryImpl(
    private val jpaRepository: ProcessingStatusSensorEventEntityRepository
) : ProcessingStatusSensorEventRepository {
    override fun setProcessingStatusToInProgress(event: SensorEvent, sequenceNumber: Int) {
        jpaRepository.saveAndFlush(
            ProcessingStatusSensorEventEntity(
                eventId = event.eventId,
                sequenceNumber = sequenceNumber,
                isProcessing = true,
                processedAt = null
            )
        )
    }

    @Transactional
    override fun setProcessingStatusToCompleted(event: SensorEvent, processedAt: Instant) {
        jpaRepository.markCompleted(event.eventId, processedAt)
    }

    override fun isProcessing(eventId: String): Boolean =
        jpaRepository.findById(eventId).orElse(null)?.isProcessing == true

    override fun allEventsCompleted(events: List<SensorEvent>): Boolean =
        !jpaRepository.existsByEventIdInAndProcessedAtIsNull(events.map { it.eventId })

    override fun getMaxSequenceNumber(): Int = jpaRepository.findMaxSequenceNumber()
}
