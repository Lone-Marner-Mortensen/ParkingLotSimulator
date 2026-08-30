package parkinglot.simulator.connector.sensor.system.adapter

import org.springframework.stereotype.Component
import parkinglot.simulator.domain.model.SensorEvent

@Component
class TreatmentStatusSensorEventRepository(
    private val jpaRepository: TreatmentStatusSensorEventEntityRepository
) {
    fun underTreatment(event: SensorEvent): Boolean =
        jpaRepository.existsById(event.eventId)

    fun markAsUnderTreatment(event: SensorEvent) {
        jpaRepository.saveAndFlush(
            TreatmentStatusSensorEventEntity(
                eventId = event.eventId
            )
        )
    }

    fun unmarkAsUnderTreatment(event: SensorEvent) {
        jpaRepository.deleteById(event.eventId)
    }
}
