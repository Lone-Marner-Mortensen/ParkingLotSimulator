package parkinglot.simulator.repository.jpa.adapter

import org.springframework.data.jpa.repository.JpaRepository
import parkinglot.simulator.repository.entity.ParkingSpotEntity

interface ParkingSpotEntityRepository : JpaRepository<ParkingSpotEntity, String> {
    fun findAllByLicensePlateIsNull(): List<ParkingSpotEntity>
}
