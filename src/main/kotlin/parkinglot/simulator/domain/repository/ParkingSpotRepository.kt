package parkinglot.simulator.domain.repository

interface ParkingSpotRepository {
    fun occupyParkingSpot(licensePlate: String, spotId: String)
    fun releaseParkingSpot(spotId: String)
    fun releaseAllParkingSpots()
    fun getFreeParkingSpots(): List<String>
    fun existsBySpotId(spotId: String): Boolean
}
