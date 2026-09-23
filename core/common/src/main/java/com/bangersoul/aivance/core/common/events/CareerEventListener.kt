package com.bangersoul.aivance.core.common.events

/**
 * Functional interface / listener abstraction for lifecycle-aware or reactive components.
 *
 * Allows decoupled observers, background workers, or analytics consumers to react
 * to domain events dispatched via [CareerEventBus] without managing direct coroutine collections.
 */
fun interface CareerEventListener {
    /**
     * Called when a [CareerEvent] is received.
     *
     * @param event The received career event.
     */
    suspend fun onEvent(event: CareerEvent)
}

/**
 * Strongly-typed functional interface / listener abstraction for components
 * observing a specific [CareerEvent] family.
 */
fun interface TypedCareerEventListener<in T : CareerEvent> {
    /**
     * Called when a specific event of type [T] is received.
     *
     * @param event The received typed event.
     */
    suspend fun onEvent(event: T)
}
