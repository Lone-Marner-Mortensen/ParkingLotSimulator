package parkinglot.simulator.domain

import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
class ParkingLotManager(
    private val parkingLifecycleService: ParkingLifeCycleService
) : SensorEventHandler {
    private val vehicleEnteringCoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override suspend fun handle(event: SensorEvent) {
        logger.info("Handling sensor event {}", event)
        when (event) {
            is VehicleEnteringEvent -> vehicleEnteringCoroutineScope.launch {
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
        runBlocking {
            vehicleEnteringCoroutineScope.coroutineContext[Job]?.cancelAndJoin()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ParkingLotManager::class.java)
    }
}
