package parkinglot.simulator.repository.jpa.adapter

import org.springframework.data.jpa.repository.JpaRepository
import parkinglot.simulator.repository.entity.VehicleTransitEntity

interface VehicleTransitEntityRepository : JpaRepository<VehicleTransitEntity, String>
