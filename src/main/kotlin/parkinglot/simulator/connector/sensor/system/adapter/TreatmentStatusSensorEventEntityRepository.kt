package parkinglot.simulator.connector.sensor.system.adapter

import org.springframework.data.jpa.repository.JpaRepository

interface TreatmentStatusSensorEventEntityRepository : JpaRepository<TreatmentStatusSensorEventEntity, String>