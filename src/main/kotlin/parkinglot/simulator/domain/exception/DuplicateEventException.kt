package parkinglot.simulator.domain.exception

import parkinglot.simulator.domain.model.SensorEvent

class DuplicateEventException(val event: SensorEvent, message: String) : Exception(message)
