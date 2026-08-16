package parkinglot.simulator.domain

import arrow.core.raise.either
import arrow.fx.coroutines.parZip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import parkinglot.simulator.connector.sensor.system.EventPublisher
import parkinglot.simulator.domain.connector.LicensePlateReader
import parkinglot.simulator.domain.connector.ParkingGuardNotifier
import parkinglot.simulator.domain.connector.PaymentStatusChecker
import parkinglot.simulator.domain.connector.VehicleSizeEstimator
import parkinglot.simulator.domain.model.SensorEvent.*

class ParkingController(
    private val eventPublisher: EventPublisher,
    val licensePlateReader: LicensePlateReader,
    val vehicleSizeEstimator: VehicleSizeEstimator,
    val paymentStatusChecker: PaymentStatusChecker,
    val parkingGuardNotifier: ParkingGuardNotifier,
    private val coroutineScope: CoroutineScope
) {

    fun startSystem() {
        coroutineScope.launch {
            eventPublisher.observeEvents().collect { event ->
                when (event) {
                    is VehicleEnteringEvent -> {
                        onVehicleEntering(event)
                    }

                    is VehicleLeavingEvent -> {
                        onVehicleLeavingEvent(event)
                    }

                    is ParkingSpotOccupiedEvent -> {
                        onParkingSpotOccupiedEvent(event)
                    }

                    is ParkingSpotReleasedEvent -> {
                        onParkingSpotReleasedEvent(event)
                    }

                    is OverStayingEvent -> {
                        onOverStayingEvent(event)
                    }
                }
            }
        }
    }

    private suspend fun onVehicleEntering(event: VehicleEnteringEvent) {
        parZip(
            { licensePlateReader.read(event.location) },
            { vehicleSizeEstimator.isVehicleTooBig(event.location) },
            { paymentStatusChecker.isPaymentComplete(event.location) }
        ) { plate, size, payment ->
            either {
                size.bind()
                payment.bind()
                plate.bind()
            }
        }.fold(
            { reason -> parkingGuardNotifier.denyEntry(event.location, reason) },
            { licensePlate -> //Update number of cars entering
            }
        )
    }

    private fun onVehicleLeavingEvent(event: VehicleLeavingEvent) {
        //Update number of cars leaving in database
    }

    private fun onParkingSpotOccupiedEvent(event: ParkingSpotOccupiedEvent) {
        //Update parking spot occupied in database
    }

    private fun onParkingSpotReleasedEvent(event: ParkingSpotReleasedEvent) {
        //Update parking spot released in database
    }

    private fun onOverStayingEvent(event: OverStayingEvent) {
        parkingGuardNotifier.vehicleHasOverStayed(event.plateNumber, event.spotId, event.duration)
    }

    fun getNumberOfCarsWhoCanEnter(): Int {
        // Get number of available parking spots from database
        // Get number of cars leaving from database
        // Get number of cars entering from database

        TODO("Implement logic to return the number of cars who can enter")
    }

    fun getFreeParkingSpots(): Int {
        TODO("Implement logic to return the number of free parking spots")
    }

    fun stopSystem() {
        coroutineScope.cancel()
    }
}
