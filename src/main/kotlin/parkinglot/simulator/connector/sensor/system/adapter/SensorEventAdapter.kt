package parkinglot.simulator.connector.sensor.system.adapter

import jakarta.annotation.PreDestroy

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CompletableDeferred
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component
import parkinglot.simulator.domain.connector.SensorEventHandler
import parkinglot.simulator.domain.connector.SensorEventSource
import parkinglot.simulator.domain.exception.DuplicateEventException
import parkinglot.simulator.domain.exception.InvalidEventException
import parkinglot.simulator.domain.validator.EventValidator
import parkinglot.simulator.domain.repository.ProcessingStatusSensorEventRepository
import parkinglot.simulator.domain.model.SensorEvent
import parkinglot.simulator.domain.model.SensorEvent.ParkingSpotOccupiedEvent
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds


// Making sure events are processed exactly once, are valid and retries 3 times in case of failure
@Component
class SensorEventAdapter(
    private val eventSource: SensorEventSource,
    private val eventHandler: SensorEventHandler,
    private val eventValidator: EventValidator,
    private val processingStatusSensorEventRepository: ProcessingStatusSensorEventRepository,
    private val meterRegistry: MeterRegistry,
    @Value("\${parking.sensor.events.earlier-events-poll-interval-ms:300}")
    earlierEventsPollIntervalMs: Long = 300,
    @Value("\${parking.sensor.events.earlier-events-max-polls:10}")
    private val earlierEventsMaxPolls: Int = 10
) : SmartLifecycle {
    private val earlierEventsPollInterval: Duration = earlierEventsPollIntervalMs.milliseconds
    private var failure = CompletableDeferred<Throwable>()
    private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        failure.complete(exception)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)
    private var consumerJob: Job? = null

    override fun start() {
        if (isRunning) return

        // Resume from the persisted sequence number so ordering survives restarts
        var sequenceNumber = processingStatusSensorEventRepository.getMaxSequenceNumber()
        consumerJob = scope.launch {
            // All events are processed synchronously except for VehicleEnteringEvent, see eventHandler.handle(event).
            eventSource.observeEvents().collect { event ->
                    if (!awaitEventValidity(event)) {
                        meterRegistry.counter("parking.sensor.events", "outcome", "invalid").increment()

                        val errorDescription = if (event is ParkingSpotOccupiedEvent)
                            "A ParkingSpotOccupiedEvent is only valid if the vehicle was already in transit. " +
                            "At this point, users must manually add the vehicle to vehicleTransitRepository for the event to become valid."
                        else
                            "Event ordering is violated."

                        throw InvalidEventException(event, "Invalid sensor event $event. $errorDescription")
                    }

                    if (processingStatusSensorEventRepository.isProcessing(event.eventId)) {
                        meterRegistry.counter("parking.sensor.events", "outcome", "duplicate").increment()
                        throw DuplicateEventException(event, "Duplicate sensor event $event")
                    }

                    sequenceNumber++
                    processingStatusSensorEventRepository.setProcessingStatusToInProgress(event, sequenceNumber)

                    try {
                        processWithRetry(event)
                        meterRegistry.counter("parking.sensor.events", "outcome", "processed").increment()
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        meterRegistry.counter("parking.sensor.events", "outcome", "failed").increment()
                        logger.error(
                            "Sensor event {} failed after {} attempts and was released for redelivery",
                            event.eventId,
                            EVENT_PROCESSING_ATTEMPTS,
                            exception
                        )
                        throw exception
                    }
            }
        }
    }

    override fun stop() {
        consumerJob?.cancel()
        consumerJob = null
    }

    override fun isRunning(): Boolean = consumerJob?.isActive == true

    internal suspend fun awaitFailure(): Nothing = throw failure.await()

    private suspend fun awaitEventValidity(event: SensorEvent): Boolean {
        // Events may be invalid because prior events has not completed yet.
        repeat(earlierEventsMaxPolls) { attempt ->
            if (eventValidator.isValid(event)) {
                return true
            }
            if (attempt < earlierEventsMaxPolls - 1) {
                logger.info("Waiting for sensor event {} to become valid", event.eventId)
                delay(earlierEventsPollInterval)
            }
        }
        return false
    }

    private suspend fun processWithRetry(event: SensorEvent) {
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
