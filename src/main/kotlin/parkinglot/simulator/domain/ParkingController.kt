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
import parkinglot.simulator.domain.model.DenyEntryReason.NO_AVAILABLE_PARKING_SPOTS
import parkinglot.simulator.domain.model.SensorEvent.*
import parkinglot.simulator.domain.repository.ParkingSpotRepository
import parkinglot.simulator.domain.repository.VehicleTransitRepository

class ParkingController(
    private val eventPublisher: EventPublisher,
    val licensePlateReader: LicensePlateReader,
    val vehicleSizeEstimator: VehicleSizeEstimator,
    val paymentStatusChecker: PaymentStatusChecker,
    val parkingGuardNotifier: ParkingGuardNotifier,
    private val vehicleTransitRepository: VehicleTransitRepository,
    private val parkingSpotRepository: ParkingSpotRepository,
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
            { licensePlateReader.read() },
            { vehicleSizeEstimator.isVehicleTooBig() },
            { paymentStatusChecker.isPaymentComplete() }
        ) { plate, size, payment ->
            either {
                size.bind()
                payment.bind()
                plate.bind()
            }
        }.fold(
            { reason -> parkingGuardNotifier.denyEntry(reason) },
            { plate ->
                if(getNumberOfVehiclesWhoCanEnter() > 0) {
                    vehicleTransitRepository.addVehicleInTransit(plate)
                }
                else {
                    // This happens only in very rare occasions because people can't go to the payment machine if
                    // there are no available parking spots. However sometimes the sensor system make mistakes
                    // If it happens the client will get his money back unless he/she decides to wait for a spot to be released.
                    parkingGuardNotifier.denyEntry(NO_AVAILABLE_PARKING_SPOTS)
                }
            }
        )
    }

    private fun onVehicleLeavingEvent(event: VehicleLeavingEvent) {
        // If the vehicle is already in transit, nothing happens.
        vehicleTransitRepository.addVehicleInTransit(event.licensePlate.value)
    }

    private fun onParkingSpotOccupiedEvent(event: ParkingSpotOccupiedEvent) {
        parkingSpotRepository.occupyParkingSpot(event.licensePlate, event.spotId)
        vehicleTransitRepository.removeVehicleInTransit(event.licensePlate.value)
    }

    private fun onParkingSpotReleasedEvent(event: ParkingSpotReleasedEvent) {
        parkingSpotRepository.releaseParkingSpot(event.licensePlate, event.spotId)
        vehicleTransitRepository.removeVehicleInTransit(event.licensePlate.value)
    }

    private fun onOverStayingEvent(event: OverStayingEvent) {
        parkingGuardNotifier.vehicleHasOverStayed(event.licensePlate, event.spotId, event.duration)
    }

    fun getNumberOfVehiclesWhoCanEnter(): Int {

        val numberOfVehiclesInTransit = vehicleTransitRepository.getNumberOfVehiclesInTransit()
        val numberOfFreeParkingSpots = parkingSpotRepository.getFreeParkingSpots().size

        // Since the vehicle-entering event are treated sequentially (see README),
        // and there are no significant delay in registering the implications (regarding number
        // of available parking spots) of the other events, we can trust the calculation.
        return numberOfFreeParkingSpots - numberOfVehiclesInTransit
    }

    fun getFreeParkingSpots(): List<String> {
        return parkingSpotRepository.getFreeParkingSpots()
    }

    fun stopSystem() {
        coroutineScope.cancel()
    }
}
