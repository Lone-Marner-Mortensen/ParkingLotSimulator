package parkinglot.simulator.fakeservice

import parkinglot.simulator.domain.model.LicensePlate
import parkinglot.simulator.domain.model.ParkingSpotId
import parkinglot.simulator.domain.repository.ParkingSpotRepository

class FakeParkingSpotRepository : ParkingSpotRepository {
    val occupiedSpots = mutableListOf<Pair<LicensePlate, ParkingSpotId>>()
    override fun occupyParkingSpot(licensePlate: LicensePlate, spotId: ParkingSpotId) {
        occupiedSpots.add(licensePlate to spotId)
    }
    override fun releaseParkingSpot(licensePlate: LicensePlate, spotId: ParkingSpotId) {}
    override fun getFreeParkingSpots(): List<String> = emptyList()
}
