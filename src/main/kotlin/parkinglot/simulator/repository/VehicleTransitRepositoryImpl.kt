package parkinglot.simulator.repository

import org.springframework.stereotype.Component
import parkinglot.simulator.domain.model.VehicleTransit
import parkinglot.simulator.domain.repository.VehicleTransitRepository
import parkinglot.simulator.repository.jpa.adapter.VehicleTransitEntityRepository
import parkinglot.simulator.repository.mapper.VehicleTransitEntityMapper

@Component
class VehicleTransitRepositoryImpl(
    private val jpaRepository: VehicleTransitEntityRepository,
    private val mapper: VehicleTransitEntityMapper
) : VehicleTransitRepository {

    override fun addVehicleInTransit(licensePlate: String) {
        VehicleTransit(licensePlate)
            .let(mapper::toEntity)
            .let(jpaRepository::save)
    }

    override fun removeVehicleInTransit(licensePlate: String) {
        jpaRepository.deleteById(licensePlate)
    }

    override fun getNumberOfVehiclesInTransit(): Long = jpaRepository.count()
}
