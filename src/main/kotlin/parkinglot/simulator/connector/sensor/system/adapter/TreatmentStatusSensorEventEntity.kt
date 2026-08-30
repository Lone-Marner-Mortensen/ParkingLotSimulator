package parkinglot.simulator.connector.sensor.system.adapter

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "processed_sensor_events")
class TreatmentStatusSensorEventEntity(
    @Id
    @Column(name = "event_id", nullable = false)
    val eventId: String
)
