package parkinglot.simulator.domain.validator

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import parkinglot.simulator.domain.model.LicensePlate
import parkinglot.simulator.domain.model.ParkingSpotId
import parkinglot.simulator.domain.model.SensorEvent
import parkinglot.simulator.domain.repository.ParkingSpotRepository
import parkinglot.simulator.domain.repository.VehicleTransitRepository
import kotlin.test.assertEquals
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
        assertTrue(validator.isValid(SensorEvent.VehicleEnteringEvent()))
    }

    @TestFactory
    fun `validate ParkingSpotOccupiedEvent by license plate in transit`() =
        listOf(true, false).map { existsByLicensePlate ->
            val event = SensorEvent.ParkingSpotOccupiedEvent(licensePlate, spotId)
            dynamicTest("${event::class.simpleName} is ${if (existsByLicensePlate) "valid" else "invalid"} when the license plate is ${if (existsByLicensePlate) "" else "not "}found in VehicleTransitRepository") {
                every { vehicleTransitRepository.existsByLicensePlate(licensePlate.value) } returns existsByLicensePlate

                assertEquals(existsByLicensePlate, validator.isValid(event))
            }
        }

    @TestFactory
    fun `validate ParkingSpotReleasedEvent, vehicle-leaving and overstaying events by spot id`() =
        listOf<(LicensePlate, ParkingSpotId) -> SensorEvent>(
            { plate, spot -> SensorEvent.ParkingSpotReleasedEvent(plate, spot) },
            { plate, spot -> SensorEvent.VehicleLeavingEvent(plate, spot) },
            { plate, spot -> SensorEvent.OverStayingEvent(plate, spot, 5.minutes) }
        ).map { buildEvent ->
            val event = buildEvent(licensePlate, spotId)
            dynamicTest("${event::class.simpleName} is valid when the spot id is found in ParkingSpotRepository") {
                every { parkingSpotRepository.existsBySpotId(spotId.value) } returns true

                assertTrue(validator.isValid(event))
            }
        } +
            listOf<(LicensePlate, ParkingSpotId) -> SensorEvent>(
                { plate, spot -> SensorEvent.ParkingSpotReleasedEvent(plate, spot) },
                { plate, spot -> SensorEvent.VehicleLeavingEvent(plate, spot) },
                { plate, spot -> SensorEvent.OverStayingEvent(plate, spot, 5.minutes) }
            ).map { buildEvent ->
                val event = buildEvent(licensePlate, spotId)
                dynamicTest("${event::class.simpleName} is invalid when the spot id is not found in ParkingSpotRepository") {
                    every { parkingSpotRepository.existsBySpotId(spotId.value) } returns false

                    assertFalse(validator.isValid(event))
                }
            }
}
