package parkinglot.simulator.repository.mapper

import org.mapstruct.Mapper
import parkinglot.simulator.domain.model.ParkingSpot
import parkinglot.simulator.domain.model.VehicleTransit
import parkinglot.simulator.repository.dto.ParkingSpotDto
import parkinglot.simulator.repository.dto.VehicleTransitDto

@Mapper(componentModel = "spring")
interface ParkingSpotDtoMapper {
    fun toDomain(dto: ParkingSpotDto): ParkingSpot
    fun toDto(domain: ParkingSpot): ParkingSpotDto
}
