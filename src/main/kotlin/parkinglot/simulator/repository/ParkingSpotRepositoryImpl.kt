package parkinglot.simulator.repository

import org.springframework.stereotype.Repository
import parkinglot.simulator.domain.model.LicensePlate
import parkinglot.simulator.domain.model.ParkingSpot
import parkinglot.simulator.domain.model.ParkingSpotId
import parkinglot.simulator.domain.repository.ParkingSpotRepository
import parkinglot.simulator.repository.jpa.adapter.ParkingSpotEntityRepository
import parkinglot.simulator.repository.mapper.ParkingSpotDtoMapper

@Repository
class ParkingSpotRepositoryImpl(
    private val jpaRepository: ParkingSpotEntityRepository,
    private val mapper: ParkingSpotDtoMapper
) : ParkingSpotRepository {

    override fun occupyParkingSpot(licensePlate: LicensePlate, spotId: ParkingSpotId) {
        jpaRepository.findById(spotId.value).orElseThrow()
            .let(mapper::toDomain)
            .let { it.copy(licensePlate = licensePlate.value) }
            .let(mapper::toDto)
            .let(jpaRepository::save)
    }

    override fun releaseParkingSpot(licensePlate: LicensePlate, spotId: ParkingSpotId) {
        jpaRepository.findById(spotId.value).orElseThrow()
            .let(mapper::toDomain)
            .let { it.copy(licensePlate = null) }
            .let(mapper::toDto)
            .let(jpaRepository::save)
    }

    override fun getFreeParkingSpots(): List<String> =
        jpaRepository.findAllByLicensePlateIsNull()
            .map(mapper::toDomain)
            .map { it.spot }
}
