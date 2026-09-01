package parkinglot.simulator.domain.builder

import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import parkinglot.simulator.domain.model.LicensePlate
import parkinglot.simulator.domain.model.ParkingSpotId
import parkinglot.simulator.domain.model.SensorEvent
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.minutes

class SensorEventsDslTest {

    @Test
    fun `spots generates a ParkingSpotId per number in the range using the given prefix`() {
        val result = spots("A", 1..3)

        assertEquals(listOf(ParkingSpotId("A1"), ParkingSpotId("A2"), ParkingSpotId("A3")), result)
    }

    @Test
    fun `sensorEvents correctly builds a list of events`() {
        // When
        val plate = LicensePlate("AB123CD123")
        val singleSpot = ParkingSpotId("A1")
        val allSpots = spots("A", 1..3)
        val plates = listOf(LicensePlate("AB123CD123"), LicensePlate("XY987ZZ999"), LicensePlate("QW456ER456"))
        val duration = 5.minutes

        // Then
        val results = sensorEvents {
            vehicleEntering()
            occupied(plate, singleSpot)
            released(plates, allSpots)
            leaving(plates, allSpots)
            overstaying(plates, allSpots, duration)
        }

        // Expect
        val expected = listOf(
            SensorEvent.VehicleEnteringEvent(eventId = results[0].eventId),
            SensorEvent.ParkingSpotOccupiedEvent(plate, singleSpot, eventId = results[1].eventId)
        ) +
            allSpots.zip(plates).mapIndexed { index, (spot, releasedPlate) ->
                SensorEvent.ParkingSpotReleasedEvent(releasedPlate, spot, eventId = results[2 + index].eventId)
            } +
            allSpots.zip(plates).mapIndexed { index, (spot, leavingPlate) ->
                SensorEvent.VehicleLeavingEvent(leavingPlate, spot, eventId = results[5 + index].eventId)
            } +
            allSpots.zip(plates).mapIndexed { index, (spot, overstayingPlate) ->
                SensorEvent.OverStayingEvent(overstayingPlate, spot, duration, eventId = results[8 + index].eventId)
            }

        assertEquals(11, results.size)
        assertEquals(expected, results)
    }

    @TestFactory
    fun `rejects a license plates list containing duplicates`() =
        listOf<Pair<String, (List<LicensePlate>, List<ParkingSpotId>) -> List<SensorEvent>>>(
            "occupied" to { plates, allSpots -> sensorEvents { occupied(plates, allSpots) } },
            "released" to { plates, allSpots -> sensorEvents { released(plates, allSpots) } },
            "leaving" to { plates, allSpots -> sensorEvents { leaving(plates, allSpots) } },
            "overstaying" to { plates, allSpots -> sensorEvents { overstaying(plates, allSpots, 5.minutes) } }
        ).map { (name, build) ->
            dynamicTest("$name rejects a license plates list containing duplicates") {
                val allSpots = spots("A", 1..2)
                val plate = LicensePlate("AB123CD123")

                assertFailsWith<IllegalArgumentException> {
                    build(listOf(plate, plate), allSpots)
                }
            }
        }

    @Test
    fun `LicensePlate rejects a value that is not exactly 10 characters long`() {
        assertFailsWith<IllegalArgumentException> { LicensePlate("SHORT") }
        assertFailsWith<IllegalArgumentException> { LicensePlate("WAYTOOLONGPLATE") }
    }

    @Test
    fun `ParkingSpotId rejects a value outside the allowed parking spot range`() {
        listOf("A0", "A26", "B0", "B26", "C1", "AA1", "A01").forEach {
            assertFailsWith<IllegalArgumentException> { ParkingSpotId(it) }
        }
    }
}
