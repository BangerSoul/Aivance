package com.bangersoul.aivance.ai.offline

import kotlinx.coroutines.flow.Flow

/**
 * Thin abstraction over the on-device LLM runtime so the provider layer can be
 * unit-tested without the native MediaPipe library. Real implementation is
 * [MediaPipeOnDeviceLlmEngine].
 */
interface OnDeviceLlmEngine : AutoCloseable {

    /** Synchronously generate a response for [prompt]. Blocking native call. */
    fun generateResponse(prompt: String): String

    /**
     * Stream a response for [prompt] as partial text chunks.
     */
    fun streamResponse(prompt: String): Flow<String>

    override fun close()
}
