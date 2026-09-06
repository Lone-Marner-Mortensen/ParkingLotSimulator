package parkinglot.simulator.domain

import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import parkinglot.simulator.domain.service.ParkingLifeCycleService
import parkinglot.simulator.domain.connector.SensorEventHandler
import parkinglot.simulator.domain.model.SensorEvent
import parkinglot.simulator.domain.model.SensorEvent.VehicleEnteringEvent
import parkinglot.simulator.domain.model.SensorEvent.VehicleLeavingEvent
import parkinglot.simulator.domain.model.SensorEvent.ParkingSpotOccupiedEvent
import parkinglot.simulator.domain.model.SensorEvent.ParkingSpotReleasedEvent
import parkinglot.simulator.domain.model.SensorEvent.OverStayingEvent

@Service
class ParkingController(
    private val parkingLifecycleService: ParkingLifeCycleService
) : SensorEventHandler {
    private val vehicleEnteringScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override suspend fun handle(event: SensorEvent) {
        logger.info("Handling sensor event {}", event)
        when (event) {
            is VehicleEnteringEvent -> vehicleEnteringScope.launch {
                parkingLifecycleService.handleVehicleEntering(event)
            }
            is VehicleLeavingEvent ->
                parkingLifecycleService.markVehicleAsLeaving(event)
            is ParkingSpotOccupiedEvent ->
                parkingLifecycleService.occupyParkingSpot(event)
            is ParkingSpotReleasedEvent ->
                parkingLifecycleService.releaseParkingSpot(event)
            is OverStayingEvent -> parkingLifecycleService.overStaying(event)
        }
    }

    @PreDestroy
    fun close() {
        vehicleEnteringScope.cancel()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ParkingController::class.java)
    }
}
