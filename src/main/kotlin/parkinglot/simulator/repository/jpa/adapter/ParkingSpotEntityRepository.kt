package parkinglot.simulator.repository.jpa.adapter

import org.springframework.data.jpa.repository.JpaRepository
import parkinglot.simulator.repository.dto.ParkingSpotDto

interface ParkingSpotEntityRepository : JpaRepository<ParkingSpotDto, String> {
    fun findAllByLicensePlateIsNull(): List<ParkingSpotDto>
}
