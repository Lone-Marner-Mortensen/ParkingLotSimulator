package parkinglot.simulator.repository.mapper

import org.mapstruct.Mapper
import parkinglot.simulator.domain.model.VehicleTransit
import parkinglot.simulator.repository.entity.VehicleTransitEntity

@Mapper(componentModel = "spring")
interface VehicleTransitEntityMapper {
    fun toEntity(domain: VehicleTransit): VehicleTransitEntity
}
