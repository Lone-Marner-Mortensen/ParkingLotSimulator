package parkinglot.simulator.repository.jpa.adapter

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import parkinglot.simulator.repository.entity.ProcessingStatusSensorEventEntity
import java.time.Instant

interface ProcessingStatusSensorEventEntityRepository : JpaRepository<ProcessingStatusSensorEventEntity, String> {
    fun existsByEventIdInAndProcessedAtIsNull(eventIds: List<String>): Boolean

    @Modifying
    @Query(
        "UPDATE ProcessingStatusSensorEventEntity e " +
            "SET e.isProcessing = false, e.processedAt = :processedAt " +
            "WHERE e.eventId = :eventId"
    )
    fun markCompleted(@Param("eventId") eventId: String, @Param("processedAt") processedAt: Instant): Int

    @Query("SELECT COALESCE(MAX(e.sequenceNumber), 0) FROM ProcessingStatusSensorEventEntity e")
    fun findMaxSequenceNumber(): Int
}
