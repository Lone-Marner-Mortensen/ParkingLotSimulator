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
        jpaRepository.findById(spotId).orElse(null)
            ?.let(mapper::toDomain)
            ?.let { it.copy(licensePlate = licensePlate) }
            ?.let(mapper::toEntity)
            ?.let(jpaRepository::save)
    }

    override fun releaseParkingSpot(spotId: String) {
        jpaRepository.findById(spotId).orElse(null)
            ?.let(mapper::toDomain)
            ?.let { it.copy(licensePlate = null) }
            ?.let(mapper::toEntity)
            ?.let(jpaRepository::save)
    }

    @Transactional
    override fun releaseAllParkingSpots() {
        jpaRepository.releaseAll()
    }

    override fun getFreeParkingSpots(): List<String> =
        jpaRepository.findAllByLicensePlateIsNull()
            .map(mapper::toDomain)
            .map { it.spot }

    override fun existsBySpotId(spotId: String): Boolean = jpaRepository.existsById(spotId)
}
