package parkinglot.simulator.domain.repository

interface VehicleTransitRepository {
    fun addVehicleInTransit(licensePlate: String)
    fun removeVehicleInTransit(licensePlate: String)
    fun getNumberOfVehiclesInTransit(): Long
}
