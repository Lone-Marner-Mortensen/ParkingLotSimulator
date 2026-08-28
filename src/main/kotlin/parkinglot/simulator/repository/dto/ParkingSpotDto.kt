package parkinglot.simulator.repository.dto

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "parking_spots")
class ParkingSpotDto(
    @Id
    val spot: String,

    @Column(nullable = true)
    val licensePlate: String?
)
