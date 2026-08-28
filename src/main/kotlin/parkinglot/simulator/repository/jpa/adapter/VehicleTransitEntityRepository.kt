package parkinglot.simulator.repository.jpa.adapter

import org.springframework.data.jpa.repository.JpaRepository
import parkinglot.simulator.repository.dto.VehicleTransitDto

interface VehicleTransitEntityRepository : JpaRepository<VehicleTransitDto, String>
