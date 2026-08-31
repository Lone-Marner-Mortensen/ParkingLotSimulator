package parkinglot.simulator.domain.validator

import parkinglot.simulator.domain.model.SensorEvent

interface EventValidator {
    fun isValid(event: SensorEvent): Boolean
}
