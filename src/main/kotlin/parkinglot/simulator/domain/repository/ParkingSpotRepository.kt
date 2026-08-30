package parkinglot.simulator.domain.repository

interface ParkingSpotRepository {
    fun occupyParkingSpot(licensePlate: String, spotId: String)
    fun releaseParkingSpot(licensePlate: String, spotId: String)
    fun getFreeParkingSpots(): List<String>
}
