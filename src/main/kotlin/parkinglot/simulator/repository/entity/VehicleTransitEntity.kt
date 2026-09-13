package parkinglot.simulator.repository.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "vehicle_transits")
class VehicleTransitEntity(
    @Id
    @Column(name = "license_plate")
    val licensePlate: String
)
