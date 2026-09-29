package com.bangersoul.aivance.ai.offline

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * [OnDeviceLlmEngine] backed by MediaPipe LLM Inference (`tasks-genai`).
 *
 * Wraps [LlmInference] for on-device generation and streaming. The model file
 * must already be present at [modelPath] (see GemmaOnDeviceProvider, which
 * downloads it at runtime).
 */
class MediaPipeOnDeviceLlmEngine private constructor(
    private val llm: LlmInference
) : OnDeviceLlmEngine {

    override fun generateResponse(prompt: String): String {
        return llm.generateResponse(prompt)
    }

    override fun streamResponse(prompt: String): Flow<String> = callbackFlow {
        // generateResponseAsync(input, ProgressListener): the listener receives
        // each partial result with a `done` flag on completion.
        llm.generateResponseAsync(prompt) { partial, done ->
            if (partial.isNotEmpty()) {
                trySend(partial)
            }
            if (done) {
                close()
            }
        }
        awaitClose { /* stream cancelled by the library on close() */ }
    }

    override fun close() {
        runCatching { llm.close() }
    }

    companion object {
        /**
         * Creates the engine and loads the model.
         *
         * @param backend Hardware acceleration hint; DEFAULT lets the runtime pick
         *   (CPU on low-end devices, GPU when available).
         */
        fun create(
            context: Context,
            modelPath: String,
            backend: LlmInference.Backend = LlmInference.Backend.DEFAULT,
            maxTokens: Int = 512
        ): MediaPipeOnDeviceLlmEngine {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(maxTokens)
                .setPreferredBackend(backend)
                .build()
            return MediaPipeOnDeviceLlmEngine(
                LlmInference.createFromOptions(context, options)
            )
        }
    }
}
