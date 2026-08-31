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
        val plate = LicensePlate("AB123CD123")
        val singleSpot = spots("A", 1..1).single()
        val allSpots = spots("A", 1..3)
        val plates = listOf(LicensePlate("AB123CD123"), LicensePlate("XY987ZZ999"), LicensePlate("QW456ER456"))
        val duration = 5.minutes

        val result = sensorEvents {
            vehicleEntering()
            occupied(plate, singleSpot)
            released(spotIds = allSpots)
            leaving(plates, allSpots)
            overstaying(plate, spotIds = allSpots, duration = duration)
        }

        val releasedPlates = result.subList(2, 2 + allSpots.size).map { (it as SensorEvent.ParkingSpotReleasedEvent).licensePlate }

        val expected = listOf(
            SensorEvent.VehicleEnteringEvent(eventId = result[0].eventId),
            SensorEvent.ParkingSpotOccupiedEvent(plate, singleSpot, eventId = result[1].eventId)
        ) +
            allSpots.zip(releasedPlates).mapIndexed { index, (spot, releasedPlate) ->
                SensorEvent.ParkingSpotReleasedEvent(releasedPlate, spot, eventId = result[2 + index].eventId)
            } +
            allSpots.zip(plates).mapIndexed { index, (spot, leavingPlate) ->
                SensorEvent.VehicleLeavingEvent(leavingPlate, spot, eventId = result[5 + index].eventId)
            } +
            allSpots.mapIndexed { index, spot ->
                SensorEvent.OverStayingEvent(plate, spot, duration, eventId = result[8 + index].eventId)
            }

        assertEquals(11, result.size)
        assertEquals(expected, result)
    }

    @TestFactory
    fun `generates a distinct random license plate per spot when no license plates are given`() =
        listOf<Pair<String, (List<ParkingSpotId>) -> List<LicensePlate>>>(
            "occupied" to { allSpots ->
                sensorEvents { occupied(spotIds = allSpots) }.map { (it as SensorEvent.ParkingSpotOccupiedEvent).licensePlate }
            },
            "released" to { allSpots ->
                sensorEvents { released(spotIds = allSpots) }.map { (it as SensorEvent.ParkingSpotReleasedEvent).licensePlate }
            },
            "leaving" to { allSpots ->
                sensorEvents { leaving(spotIds = allSpots) }.map { (it as SensorEvent.VehicleLeavingEvent).licensePlate }
            },
            "overstaying" to { allSpots ->
                sensorEvents { overstaying(spotIds = allSpots, duration = 5.minutes) }
                    .map { (it as SensorEvent.OverStayingEvent).licensePlate }
            }
        ).map { (name, generatePlates) ->
            dynamicTest("$name generates a distinct random license plate per spot when no license plates are given") {
                val allSpots = spots("A", 1..5)

                val licensePlates = generatePlates(allSpots)

                assertEquals(allSpots.size, licensePlates.distinct().size)
            }
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

                println("build = $build")

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
