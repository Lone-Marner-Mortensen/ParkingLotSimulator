package parkinglot.simulator.connector.sensor.system.adapter

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "sensor_events_processing_status")
class ProcessingStatusSensorEventEntity(
    @Id
    @Column(name = "event_id", nullable = false)
    val eventId: String,
    @Column(name = "sequence_number")
    val sequenceNumber: Int?,
    @Column(name = "is_processing")
    val isProcessing: Boolean?,
    @Column(name = "processed_at")
    val processedAt: Instant?
)
