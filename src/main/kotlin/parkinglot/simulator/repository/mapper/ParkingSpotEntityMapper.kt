package parkinglot.simulator.repository.mapper

import org.mapstruct.Mapper
import parkinglot.simulator.domain.model.ParkingSpot
import parkinglot.simulator.repository.entity.ParkingSpotEntity

@Mapper(componentModel = "spring")
interface ParkingSpotEntityMapper {
    fun toDomain(entity: ParkingSpotEntity): ParkingSpot
    fun toEntity(domain: ParkingSpot): ParkingSpotEntity
}
