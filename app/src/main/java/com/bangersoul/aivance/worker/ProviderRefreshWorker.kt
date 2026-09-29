package com.bangersoul.aivance.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.bangersoul.aivance.core.domain.usecase.provider.GetAvailableModelsUseCase
import com.bangersoul.aivance.core.domain.usecase.provider.GetProviderHealthUseCase
import com.bangersoul.aivance.core.domain.usecase.provider.ProviderHealth
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Periodic worker that checks the health of configured AI providers
 * and refreshes their available model lists.
 *
 * Scheduled every 6 hours. Also emitted as one-time work after a
 * provider configuration change.
 */
@HiltWorker
class ProviderRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val getProviderHealthUseCase: GetProviderHealthUseCase,
    private val getAvailableModelsUseCase: GetAvailableModelsUseCase,
    private val connectivityMonitor: ConnectivityMonitor
) : CoroutineWorker(context, params) {

    companion object {
        val knownProviders = listOf("gemini", "openai", "groq", "openrouter", "ollama", "anthropic", "gemma")
    }

    override suspend fun doWork(): ListenableWorker.Result {
        Timber.d("ProviderRefreshWorker started")

        if (!connectivityMonitor.isOnline) {
            Timber.d("ProviderRefreshWorker — offline, deferring")
            return ListenableWorker.Result.retry()
        }

        var healthyCount = 0
        var modelCount = 0

        try {
            for (providerId in knownProviders) {
                // Check health
                val healthResult = getProviderHealthUseCase(providerId)
                @Suppress("UNCHECKED_CAST")
                when (healthResult) {
                    is com.bangersoul.aivance.core.common.result.Result.Success<*> -> {
                        val health = (healthResult as com.bangersoul.aivance.core.common.result.Result.Success<ProviderHealth>).data
                        if (health.isOperational) {
                            healthyCount++
                        }
                    }
                    is com.bangersoul.aivance.core.common.result.Result.Failure -> {
                        Timber.w("ProviderRefreshWorker — %s health check failed: %s",
                            providerId, (healthResult as com.bangersoul.aivance.core.common.result.Result.Failure).error.message)
                    }
                }

                // Refresh models
                val modelsResult = getAvailableModelsUseCase(providerId)
                @Suppress("UNCHECKED_CAST")
                when (modelsResult) {
                    is com.bangersoul.aivance.core.common.result.Result.Success<*> -> {
                        val models = (modelsResult as com.bangersoul.aivance.core.common.result.Result.Success<List<String>>).data
                        modelCount += models.size
                    }
                    is com.bangersoul.aivance.core.common.result.Result.Failure -> {
                        Timber.w("ProviderRefreshWorker — %s model refresh failed: %s",
                            providerId, (modelsResult as com.bangersoul.aivance.core.common.result.Result.Failure).error.message)
                    }
                }
            }

            Timber.d("ProviderRefreshWorker completed — %d healthy providers, %d models",
                healthyCount, modelCount)
            return ListenableWorker.Result.success()

        } catch (e: Exception) {
            Timber.e(e, "ProviderRefreshWorker failed")
            return if (runAttemptCount < 3) ListenableWorker.Result.retry() else ListenableWorker.Result.failure()
        }
    }
}
