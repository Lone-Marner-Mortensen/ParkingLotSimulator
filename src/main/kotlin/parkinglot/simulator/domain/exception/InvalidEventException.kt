package parkinglot.simulator.domain.exception

import parkinglot.simulator.domain.model.SensorEvent

class InvalidEventException(val event: SensorEvent, message: String) : Exception(message)
