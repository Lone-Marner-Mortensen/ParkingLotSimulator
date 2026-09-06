package parkinglot.simulator.domain.validator

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import parkinglot.simulator.domain.model.LicensePlate
import parkinglot.simulator.domain.model.ParkingSpotId
import parkinglot.simulator.domain.model.SensorEvent
import parkinglot.simulator.domain.model.SensorEvent.VehicleEnteringEvent
import parkinglot.simulator.domain.model.SensorEvent.ParkingSpotOccupiedEvent
import parkinglot.simulator.domain.model.SensorEvent.ParkingSpotReleasedEvent
import parkinglot.simulator.domain.model.SensorEvent.VehicleLeavingEvent
import parkinglot.simulator.domain.model.SensorEvent.OverStayingEvent
import parkinglot.simulator.domain.repository.ParkingSpotRepository
import parkinglot.simulator.domain.repository.VehicleTransitRepository
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class EventValidatorImplTest {
    private val vehicleTransitRepository = mockk<VehicleTransitRepository>()
    private val parkingSpotRepository = mockk<ParkingSpotRepository>()
    private val validator = EventValidatorImpl(vehicleTransitRepository, parkingSpotRepository)

    private val licensePlate = LicensePlate("AB123CD123")
    private val spotId = ParkingSpotId("A1")

    @Test
    fun `VehicleEnteringEvent is always valid`() {
        assertTrue(validator.isValid(VehicleEnteringEvent()))
    }

    @Test
    fun `ParkingSpotOccupiedEvent is valid when the license plate is found in VehicleTransitRepository`() {
        val event = ParkingSpotOccupiedEvent(licensePlate, spotId)
        every { vehicleTransitRepository.existsByLicensePlate(licensePlate.value) } returns true

        assertTrue(validator.isValid(event))
    }

    @Test
    fun `ParkingSpotOccupiedEvent is invalid when the license plate is not found in VehicleTransitRepository`() {
        val event = ParkingSpotOccupiedEvent(licensePlate, spotId)
        every { vehicleTransitRepository.existsByLicensePlate(licensePlate.value) } returns false

        assertFalse(validator.isValid(event))
    }

    @TestFactory
    fun `validate ParkingSpotReleasedEvent, vehicle-leaving and overstaying events by spot id`() =
        listOf<(LicensePlate, ParkingSpotId) -> SensorEvent>(
            { plate, spot -> ParkingSpotReleasedEvent(plate, spot) },
            { plate, spot -> VehicleLeavingEvent(plate, spot) },
            { plate, spot -> OverStayingEvent(plate, spot, 5.minutes) }
        ).map { buildEvent ->
            val event = buildEvent(licensePlate, spotId)
            dynamicTest("${event::class.simpleName} is valid when the spot id is found in ParkingSpotRepository") {
                every { parkingSpotRepository.existsBySpotId(spotId.value) } returns true

                assertTrue(validator.isValid(event))
            }
        } +
            listOf<(LicensePlate, ParkingSpotId) -> SensorEvent>(
                { plate, spot -> ParkingSpotReleasedEvent(plate, spot) },
                { plate, spot -> VehicleLeavingEvent(plate, spot) },
                { plate, spot -> OverStayingEvent(plate, spot, 5.minutes) }
            ).map { buildEvent ->
                val event = buildEvent(licensePlate, spotId)
                val eventName = event::class.simpleName
                dynamicTest("$eventName is invalid when the spot id is not found in ParkingSpotRepository") {
                    every { parkingSpotRepository.existsBySpotId(spotId.value) } returns false

                    assertFalse(validator.isValid(event))
                }
            }
}
