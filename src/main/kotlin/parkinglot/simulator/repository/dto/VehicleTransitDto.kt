package parkinglot.simulator.repository.dto

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "vehicle_transits")
class VehicleTransitDto(
    @Id
    val licensePlate: String
)
