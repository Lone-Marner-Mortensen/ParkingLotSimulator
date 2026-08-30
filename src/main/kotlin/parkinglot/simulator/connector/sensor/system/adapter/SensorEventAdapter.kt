package parkinglot.simulator.connector.sensor.system.adapter

import jakarta.annotation.PreDestroy

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component
import parkinglot.simulator.domain.connector.SensorEventHandler
import parkinglot.simulator.domain.connector.SensorEventSource
import kotlin.time.Duration.Companion.milliseconds


// Making sure events are processed exactly once and retries 3 times in case of failure
@Component
class SensorEventAdapter(
    private val eventSource: SensorEventSource,
    private val eventHandler: SensorEventHandler,
    private val treatmentStatusRepository: TreatmentStatusSensorEventRepository,
    private val meterRegistry: MeterRegistry
) : SmartLifecycle {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var consumerJob: Job? = null

    override fun start() {
        if (isRunning) return

        consumerJob = scope.launch {
            eventSource.observeEvents().collect { event ->
                if (treatmentStatusRepository.underTreatment(event)) {
                    meterRegistry.counter("parking.sensor.events", "outcome", "duplicate").increment()
                    logger.info("Ignoring duplicate sensor event {}", event.eventId)
                    return@collect
                }
                treatmentStatusRepository.markAsUnderTreatment(event)

                try {
                    processWithRetry(event)
                    meterRegistry.counter("parking.sensor.events", "outcome", "processed").increment()
                } catch (exception: CancellationException) {
                    treatmentStatusRepository.unmarkAsUnderTreatment(event)
                    throw exception
                } catch (exception: Exception) {
                    treatmentStatusRepository.unmarkAsUnderTreatment(event)
                    meterRegistry.counter("parking.sensor.events", "outcome", "failed").increment()
                    logger.error(
                        "Sensor event {} failed after {} attempts and was released for redelivery",
                        event.eventId,
                        EVENT_PROCESSING_ATTEMPTS,
                        exception
                    )
                }
            }
        }
    }

    override fun stop() {
        consumerJob?.cancel()
        consumerJob = null
    }

    override fun isRunning(): Boolean = consumerJob?.isActive == true

    private suspend fun processWithRetry(event: parkinglot.simulator.domain.model.SensorEvent) {
        repeat(EVENT_PROCESSING_ATTEMPTS) { attempt ->
            try {
                eventHandler.handle(event)
                return
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (attempt == EVENT_PROCESSING_ATTEMPTS - 1) throw exception
                logger.warn(
                    "Retrying sensor event {} after processing failure (attempt {}/{})",
                    event.eventId,
                    attempt + 1,
                    EVENT_PROCESSING_ATTEMPTS,
                    exception
                )
                meterRegistry.counter("parking.sensor.events", "outcome", "retry").increment()
                delay(EVENT_RETRY_DELAY)
            }
        }
    }

    @PreDestroy
    fun close() {
        scope.cancel()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SensorEventAdapter::class.java)
        private const val EVENT_PROCESSING_ATTEMPTS = 3
        private val EVENT_RETRY_DELAY = 100.milliseconds
    }
}
