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
import kotlinx.coroutines.withTimeoutOrNull
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds


// Making sure events are processed exactly once, are valid and retries 3 times in case of failure
@Component
class SensorEventAdapter(
    private val eventSource: SensorEventSource,
    private val eventHandler: SensorEventHandler,
    private val eventValidator: EventValidator,
    private val processingStatusSensorEventRepository: ProcessingStatusSensorEventRepository,
    private val meterRegistry: MeterRegistry,
    @Value("\${parking.sensor.events.earlier-events-completion-timeout-ms:5000}")
    earlierEventsCompletionTimeoutMs: Int
) : SmartLifecycle {
    private val earlierEventsCompletionTimeout: Duration = earlierEventsCompletionTimeoutMs.milliseconds
    private var failure = CompletableDeferred<Throwable>()
    private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        failure.complete(exception)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)
    private var consumerJob: Job? = null

    override fun start() {
        if (isRunning) return

        var sequenceNumber = 0;
        consumerJob = scope.launch {
                eventSource.observeEvents().collect { event ->
                    if (!eventValidator.isValid(event)) {
                        awaitEarlierEventsCompletion(sequenceNumber + 1, earlierEventsCompletionTimeout)

                        if (!eventValidator.isValid(event)) {
                            meterRegistry.counter("parking.sensor.events", "outcome", "invalid").increment()
                            throw InvalidEventException(event, "Invalid sensor event $event. Event ordering is violated.")
                        }
                    }

                    if (processingStatusSensorEventRepository.isProcessing(event.eventId)) {
                        meterRegistry.counter("parking.sensor.events", "outcome", "duplicate").increment()
                        throw DuplicateEventException(event, "Duplicate sensor event $event")
                    }

                    sequenceNumber++;
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

    private suspend fun awaitEarlierEventsCompletion(sequenceNumber: Int, maxWait: Duration) {
        withTimeoutOrNull(maxWait) {
            while (!processingStatusSensorEventRepository.isEarlierEventsCompleted(sequenceNumber)) {
                delay(EARLIER_EVENTS_POLL_INTERVAL)
            }
        }
    }

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
        private val EARLIER_EVENTS_POLL_INTERVAL = 50.milliseconds
    }
}
