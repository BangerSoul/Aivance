package com.bangersoul.aivance.core.domain.repository

import com.bangersoul.aivance.core.common.result.CoreResult
import kotlinx.coroutines.flow.Flow

interface AssistantRepository {
    fun getConversations(): Flow<CoreResult<List<AssistantConversation>>>

    /**
     * Reactive history for a single conversation, oldest-first. Used to restore
     * the chat transcript after process death / app restart.
     */
    fun getMessages(conversationId: String): Flow<CoreResult<List<AssistantMessage>>>

    /**
     * Persists one message. Implementations MUST ensure the parent conversation
     * row exists first (the messages table has a CASCADE foreign key to it), so
     * the write does not fail with a foreign-key constraint violation.
     */
    suspend fun saveMessage(conversationId: String, role: String, content: String): CoreResult<Long>
}

data class AssistantConversation(
    val id: String,
    val title: String,
    val lastUpdatedAt: Long
)

data class AssistantMessage(
    val id: Long,
    val conversationId: String,
    val role: String,
    val content: String,
    val timestamp: Long
)
