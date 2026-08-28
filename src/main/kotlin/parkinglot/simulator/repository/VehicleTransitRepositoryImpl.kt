package parkinglot.simulator.repository

import org.springframework.stereotype.Repository
import parkinglot.simulator.domain.model.VehicleTransit
import parkinglot.simulator.domain.repository.VehicleTransitRepository
import parkinglot.simulator.repository.jpa.adapter.VehicleTransitEntityRepository
import parkinglot.simulator.repository.mapper.VehicleTransitDtoMapper

@Repository
class VehicleTransitRepositoryImpl(
    private val jpaRepository: VehicleTransitEntityRepository,
    private val mapper: VehicleTransitDtoMapper
) : VehicleTransitRepository {

    override fun addVehicleInTransit(licensePlate: String) {
        VehicleTransit(licensePlate)
            .let(mapper::toDto)
            .let(jpaRepository::save)
    }

    override fun removeVehicleInTransit(licensePlate: String) {
        jpaRepository.deleteById(licensePlate)
    }

    override fun getNumberOfVehiclesInTransit(): Int {
        return jpaRepository.count().toInt()
    }
}
