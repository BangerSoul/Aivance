package com.bangersoul.aivance.core.common.events

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decentralized, reactive event bus for the Career Knowledge OS.
 *
 * Implements a high-throughput, thread-safe event bus using Kotlin Coroutines [SharedFlow].
 * Allows modules (`:feature:resume`, `:feature:ats`, `:feature:jobs`, `:feature:tracker`,
 * `:feature:interview`, etc.) to communicate asynchronously with zero tight coupling.
 *
 * Configured with a configurable replay cache (default 64) and [BufferOverflow.DROP_OLDEST]
 * to prevent backpressure blocking on high-frequency bursts while ensuring new subscribers
 * immediately receive historical context.
 *
 * @param replayCacheSize Maximum number of historical events retained for replay. Defaults to 64.
 * @param extraBufferCapacity Additional buffer capacity beyond replay for high-frequency bursts. Defaults to 64.
 */
@Singleton
class CareerEventBus(
    val replayCacheSize: Int,
    val extraBufferCapacity: Int
) {
    /**
     * Dagger / Hilt constructor with default replay cache and buffer configuration.
     */
    @Inject
    constructor() : this(DEFAULT_REPLAY_CACHE_SIZE, DEFAULT_EXTRA_BUFFER_CAPACITY)

    /**
     * Convenience constructor allowing custom replay cache size with default extra buffer.
     */
    constructor(replayCacheSize: Int) : this(replayCacheSize, DEFAULT_EXTRA_BUFFER_CAPACITY)

    companion object {
        const val DEFAULT_REPLAY_CACHE_SIZE: Int = 64
        const val DEFAULT_EXTRA_BUFFER_CAPACITY: Int = 64
    }

    private val _events = MutableSharedFlow<CareerEvent>(
        replay = replayCacheSize,
        extraBufferCapacity = extraBufferCapacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Read-only [SharedFlow] of all emitted [CareerEvent]s.
     */
    val events: SharedFlow<CareerEvent> = _events.asSharedFlow()

    /**
     * Emits a [CareerEvent] into the bus.
     *
     * Because this flow is configured with [BufferOverflow.DROP_OLDEST], emission
     * never suspends due to a full buffer, guaranteeing low latency and high throughput.
     */
    suspend fun emit(event: CareerEvent) {
        _events.emit(event)
    }

    /**
     * Attempts to emit a [CareerEvent] immediately without suspending.
     *
     * @return `true` if emission succeeded, `false` otherwise.
     */
    fun tryEmit(event: CareerEvent): Boolean {
        return _events.tryEmit(event)
    }

    /**
     * Returns a snapshot list of recent events from the replay cache.
     *
     * @param count Maximum number of recent events to retrieve. Defaults to [replayCacheSize].
     * @return The most recent events in chronological order.
     */
    fun getRecentEvents(count: Int = replayCacheSize): List<CareerEvent> {
        if (count <= 0) return emptyList()
        return _events.replayCache.takeLast(count)
    }

    /**
     * Resets the replay cache of this event bus.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun resetReplayCache() {
        _events.resetReplayCache()
    }

    /**
     * Convenience subscription flow for [ResumeEvent]s.
     */
    fun resumeEvents(): Flow<ResumeEvent> = events.filterIsInstance<ResumeEvent>()

    /**
     * Convenience subscription flow for [AtsEvent]s.
     */
    fun atsEvents(): Flow<AtsEvent> = events.filterIsInstance<AtsEvent>()

    /**
     * Convenience subscription flow for [JobEvent]s.
     */
    fun jobEvents(): Flow<JobEvent> = events.filterIsInstance<JobEvent>()

    /**
     * Convenience subscription flow for [ApplicationEvent]s.
     */
    fun applicationEvents(): Flow<ApplicationEvent> = events.filterIsInstance<ApplicationEvent>()

    /**
     * Convenience subscription flow for [InterviewEvent]s.
     */
    fun interviewEvents(): Flow<InterviewEvent> = events.filterIsInstance<InterviewEvent>()

    /**
     * Convenience subscription flow for [SkillEvent]s.
     */
    fun skillEvents(): Flow<SkillEvent> = events.filterIsInstance<SkillEvent>()

    /**
     * Convenience subscription flow for [GoalEvent]s.
     */
    fun goalEvents(): Flow<GoalEvent> = events.filterIsInstance<GoalEvent>()

    /**
     * Convenience subscription flow for [AutomationEvent]s.
     */
    fun automationEvents(): Flow<AutomationEvent> = events.filterIsInstance<AutomationEvent>()

    /**
     * Convenience subscription flow for [AgentEvent]s.
     */
    fun agentEvents(): Flow<AgentEvent> = events.filterIsInstance<AgentEvent>()

    /**
     * Convenience subscription flow for [ProviderEvent]s.
     */
    fun providerEvents(): Flow<ProviderEvent> = events.filterIsInstance<ProviderEvent>()

    /**
     * Convenience subscription flow for [CoverLetterEvent]s.
     */
    fun coverLetterEvents(): Flow<CoverLetterEvent> = events.filterIsInstance<CoverLetterEvent>()

    /**
     * Convenience subscription flow for [CareerAnalyticsEvent]s.
     */
    fun analyticsEvents(): Flow<CareerAnalyticsEvent> = events.filterIsInstance<CareerAnalyticsEvent>()
}

/**
 * Inline reified extension to filter events by a specific [CareerEvent] subtype.
 *
 * Example usage:
 * ```kotlin
 * eventBus.eventsOfType<AtsEvent>().collect { atsEvent ->
 *     when (atsEvent) {
 *         is AtsEvent.ScoreCalculated -> ...
 *         is AtsEvent.OptimizationRequested -> ...
 *     }
 * }
 * ```
 */
inline fun <reified T : CareerEvent> CareerEventBus.eventsOfType(): Flow<T> =
    events.filterIsInstance<T>()

/**
 * Inline reified extension on [Flow] of [CareerEvent] to filter events by subtype.
 */
inline fun <reified T : CareerEvent> Flow<CareerEvent>.eventsOfType(): Flow<T> =
    filterIsInstance<T>()

/**
 * Subscribes a [CareerEventListener] to this event bus within the provided [CoroutineScope].
 *
 * @param scope The lifecycle-bound coroutine scope.
 * @param listener Listener callback to execute on each received event.
 * @return The [Job] representing the subscription coroutine.
 */
fun CareerEventBus.subscribe(
    scope: CoroutineScope,
    listener: CareerEventListener
): Job = scope.launch {
    events.collect { event ->
        listener.onEvent(event)
    }
}

/**
 * Subscribes a typed callback for events of type [T] within the provided [CoroutineScope].
 *
 * @param scope The lifecycle-bound coroutine scope.
 * @param onEvent Suspending lambda to execute on each received event of type [T].
 * @return The [Job] representing the subscription coroutine.
 */
inline fun <reified T : CareerEvent> CareerEventBus.subscribe(
    scope: CoroutineScope,
    crossinline onEvent: suspend (T) -> Unit
): Job = scope.launch {
    eventsOfType<T>().collect { event ->
        onEvent(event)
    }
}
