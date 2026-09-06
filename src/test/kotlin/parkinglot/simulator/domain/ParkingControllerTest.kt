package parkinglot.simulator.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test
import parkinglot.simulator.domain.model.LicensePlate
import parkinglot.simulator.domain.model.ParkingSpotId
import parkinglot.simulator.domain.service.ParkingLifeCycleService
import java.time.Duration
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import parkinglot.simulator.domain.model.SensorEvent.VehicleEnteringEvent
import parkinglot.simulator.domain.model.SensorEvent.VehicleLeavingEvent
import parkinglot.simulator.domain.model.SensorEvent.ParkingSpotOccupiedEvent
import parkinglot.simulator.domain.model.SensorEvent.ParkingSpotReleasedEvent
import parkinglot.simulator.domain.model.SensorEvent.OverStayingEvent

class ParkingControllerTest {
    private val parkingLifecycleService = mockk<ParkingLifeCycleService>(relaxed = true)
    private val handler = ParkingController(parkingLifecycleService)
    private val licensePlateValue = LicensePlate("AB123CD123")
    private val spotIdValue = ParkingSpotId("A1")

    @Test
    fun `entering, occupySpot, releaseSpot, leaving, and overstay events are delegated to their use cases`() = runTest {
        val enteringEvent = VehicleEnteringEvent()
        val occupiedEvent = ParkingSpotOccupiedEvent(licensePlateValue, spotIdValue)
        val releasedEvent = ParkingSpotReleasedEvent(licensePlateValue, spotIdValue)
        val leavingEvent = VehicleLeavingEvent(licensePlateValue, spotIdValue)
        val overStayingEvent = OverStayingEvent(licensePlateValue, spotIdValue, 15.minutes)

        handler.handle(enteringEvent)
        handler.handle(occupiedEvent)
        handler.handle(releasedEvent)
        handler.handle(leavingEvent)
        handler.handle(overStayingEvent)

        coVerify(timeout = 1_000) { parkingLifecycleService.handleVehicleEntering(enteringEvent) }
        verify { parkingLifecycleService.occupyParkingSpot(occupiedEvent) }
        verify { parkingLifecycleService.releaseParkingSpot(releasedEvent) }
        verify { parkingLifecycleService.markVehicleAsLeaving(leavingEvent) }
        verify { parkingLifecycleService.overStaying(overStayingEvent) }
    }

    @Test
    fun `handleVehicleEntering does not block handling of other events`() = runTest {
        // when
        val enteringEvent = VehicleEnteringEvent()
        val releasedEvent = ParkingSpotReleasedEvent(licensePlateValue, spotIdValue)

        var releasedProcessed = false
        var enteringCompleted = false

        coEvery { parkingLifecycleService.handleVehicleEntering(enteringEvent) } coAnswers {
            delay(500)
            enteringCompleted = true
        }
        every { parkingLifecycleService.releaseParkingSpot(releasedEvent) } answers {
            releasedProcessed = true
        }

        // then
        handler.handle(enteringEvent)
        handler.handle(releasedEvent)

        await().until { releasedProcessed }

        // expect
        assertTrue(releasedProcessed)
        assertFalse(enteringCompleted)

        await().until { enteringCompleted }
        coVerify(exactly = 1) { parkingLifecycleService.handleVehicleEntering(enteringEvent) }
        verify(exactly = 1) { parkingLifecycleService.releaseParkingSpot(releasedEvent) }
    }
}
