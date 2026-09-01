package parkinglot.simulator.repository

import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import parkinglot.simulator.domain.repository.ParkingSpotRepository
import parkinglot.simulator.repository.jpa.adapter.ParkingSpotEntityRepository
import parkinglot.simulator.repository.mapper.ParkingSpotEntityMapper

@Repository
class ParkingSpotRepositoryImpl(
    private val jpaRepository: ParkingSpotEntityRepository,
    private val mapper: ParkingSpotEntityMapper
) : ParkingSpotRepository {

    override fun occupyParkingSpot(licensePlate: String, spotId: String) {
        jpaRepository.findById(spotId)
            .map(mapper::toDomain)
            .map { it.copy(licensePlate = licensePlate) }
            .map(mapper::toEntity)
            .map(jpaRepository::save)
            .orElse(null)
    }

    override fun releaseParkingSpot(spotId: String) {
        jpaRepository.findById(spotId)
            .map(mapper::toDomain)
            .map { it.copy(licensePlate = null) }
            .map(mapper::toEntity)
            .map(jpaRepository::save)
            .orElse(null)
    }

    @Transactional
    override fun releaseAllParkingSpots() {
        jpaRepository.findAll()
            .map(mapper::toDomain)
            .map { it.copy(licensePlate = null) }
            .map(mapper::toEntity)
            .let(jpaRepository::saveAll)
    }

    override fun getFreeParkingSpots(): List<String> =
        jpaRepository.findAllByLicensePlateIsNull()
            .map(mapper::toDomain)
            .map { it.spot }

    override fun existsBySpotId(spotId: String): Boolean = jpaRepository.existsById(spotId)
}
