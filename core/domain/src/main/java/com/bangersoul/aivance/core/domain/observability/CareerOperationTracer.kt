package com.bangersoul.aivance.core.domain.observability

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

enum class TraceStatus {
    IN_PROGRESS,
    SUCCESS,
    FAILED,
    CANCELLED
}

data class OperationSpan(
    val spanId: String = UUID.randomUUID().toString(),
    val stepName: String,
    val startTimestamp: Long = System.currentTimeMillis(),
    val endTimestamp: Long? = null,
    val durationMs: Long? = null,
    val sanitizedMetadata: Map<String, String> = emptyMap()
)

data class CareerOperationTrace(
    val operationId: String,
    val title: String,
    val startTimestamp: Long = System.currentTimeMillis(),
    val endTimestamp: Long? = null,
    val status: TraceStatus = TraceStatus.IN_PROGRESS,
    val spans: List<OperationSpan> = emptyList(),
    val errorMessage: String? = null
)

enum class PlatformConnectivityState {
    ONLINE,
    OFFLINE,
    DEGRADED_PROVIDER,
    PROVIDER_FAILURE,
    DATABASE_FAILURE
}

/**
 * Structured Career Operation Tracer.
 *
 * Provides end-to-end telemetry across autonomous agent workflows without logging
 * sensitive API keys, full resume contents, or private personal data.
 */
@Singleton
class CareerOperationTracer @Inject constructor() {

    private val traces = ConcurrentHashMap<String, CareerOperationTrace>()
    private val _tracesFlow = MutableStateFlow<List<CareerOperationTrace>>(emptyList())
    val recentTraces: StateFlow<List<CareerOperationTrace>> = _tracesFlow.asStateFlow()

    private val _connectivityState = MutableStateFlow(PlatformConnectivityState.ONLINE)
    val connectivityState: StateFlow<PlatformConnectivityState> = _connectivityState.asStateFlow()

    fun updateConnectivityState(newState: PlatformConnectivityState) {
        _connectivityState.value = newState
    }

    fun startTrace(title: String, operationId: String = "trace_${UUID.randomUUID().toString().take(8)}"): CareerOperationTrace {
        val trace = CareerOperationTrace(
            operationId = operationId,
            title = title
        )
        traces[operationId] = trace
        updateState()
        return trace
    }

    fun recordSpan(
        operationId: String,
        stepName: String,
        durationMs: Long,
        metadata: Map<String, String> = emptyMap()
    ) {
        val existing = traces[operationId] ?: return
        // Sanitize any potential PII keys from metadata
        val sanitized = metadata.filterKeys { key ->
            !key.contains("key", ignoreCase = true) &&
            !key.contains("token", ignoreCase = true) &&
            !key.contains("secret", ignoreCase = true) &&
            !key.contains("email", ignoreCase = true)
        }
        val span = OperationSpan(
            stepName = stepName,
            durationMs = durationMs,
            sanitizedMetadata = sanitized
        )
        val updated = existing.copy(spans = existing.spans + span)
        traces[operationId] = updated
        updateState()
    }

    fun endTrace(operationId: String, success: Boolean, error: String? = null) {
        val existing = traces[operationId] ?: return
        val updated = existing.copy(
            endTimestamp = System.currentTimeMillis(),
            status = if (success) TraceStatus.SUCCESS else TraceStatus.FAILED,
            errorMessage = error
        )
        traces[operationId] = updated
        updateState()
    }

    private fun updateState() {
        _tracesFlow.value = traces.values.sortedByDescending { it.startTimestamp }.take(50)
    }
}
