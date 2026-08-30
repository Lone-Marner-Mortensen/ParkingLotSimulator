package parkinglot.simulator.repository.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "parking_spots")
class ParkingSpotEntity(
    @Id
    val spot: String,

    @Column(nullable = true)
    val licensePlate: String?
)
