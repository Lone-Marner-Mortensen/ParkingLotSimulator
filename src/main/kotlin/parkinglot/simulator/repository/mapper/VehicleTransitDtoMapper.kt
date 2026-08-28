package parkinglot.simulator.repository.mapper

import org.mapstruct.Mapper
import parkinglot.simulator.domain.model.VehicleTransit
import parkinglot.simulator.repository.dto.VehicleTransitDto

@Mapper(componentModel = "spring")
interface VehicleTransitDtoMapper {
    fun toDomain(dto: VehicleTransitDto): VehicleTransit
    fun toDto(domain: VehicleTransit): VehicleTransitDto
}
