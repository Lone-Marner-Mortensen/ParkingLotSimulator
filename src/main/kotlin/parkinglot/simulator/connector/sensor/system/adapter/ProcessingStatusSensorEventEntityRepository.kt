package parkinglot.simulator.connector.sensor.system.adapter

import org.springframework.data.jpa.repository.JpaRepository

interface ProcessingStatusSensorEventEntityRepository : JpaRepository<ProcessingStatusSensorEventEntity, String> {
    fun countBySequenceNumberLessThanAndProcessedAtIsNotNull(sequenceNumber: Int): Long
}
