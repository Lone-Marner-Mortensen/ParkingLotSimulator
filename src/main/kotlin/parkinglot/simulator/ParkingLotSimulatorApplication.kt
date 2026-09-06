package parkinglot.simulator

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import parkinglot.simulator.connector.sensor.system.adapter.SensorEventAdapter
import kotlin.time.Duration.Companion.milliseconds

@SpringBootApplication
@EnableScheduling
class ParkingLotSimulatorApplication

fun main(args: Array<String>) {
    val context = runApplication<ParkingLotSimulatorApplication>(*args)

    val eventAdapter = context.getBean(SensorEventAdapter::class.java)
    while (eventAdapter.isRunning) {
        Thread.sleep(200.milliseconds.inWholeMilliseconds)
    }
}
