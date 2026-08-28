package parkinglot.simulator.domain.repository

import parkinglot.simulator.domain.model.LicensePlate
import parkinglot.simulator.domain.model.ParkingSpot
import parkinglot.simulator.domain.model.ParkingSpotId
import kotlin.String

interface ParkingSpotRepository {
    fun occupyParkingSpot(licensePlate: LicensePlate, spotId: ParkingSpotId)
    fun releaseParkingSpot(licensePlate: LicensePlate, spotId: ParkingSpotId)
    fun getFreeParkingSpots(): List<String>
}
