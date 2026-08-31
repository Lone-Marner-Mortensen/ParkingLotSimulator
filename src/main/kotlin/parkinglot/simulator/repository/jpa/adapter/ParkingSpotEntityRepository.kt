package parkinglot.simulator.repository.jpa.adapter

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import parkinglot.simulator.repository.entity.ParkingSpotEntity

interface ParkingSpotEntityRepository : JpaRepository<ParkingSpotEntity, String> {
    fun findAllByLicensePlateIsNull(): List<ParkingSpotEntity>

    @Modifying
    @Query("UPDATE ParkingSpotEntity SET licensePlate = NULL")
    fun releaseAll()
}
