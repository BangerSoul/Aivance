package com.bangersoul.aivance.core.data.repository

import com.bangersoul.aivance.core.common.result.CoreResult
import com.bangersoul.aivance.core.common.result.runCatchingCore
import com.bangersoul.aivance.core.database.dao.AssistantDao
import com.bangersoul.aivance.core.database.model.AssistantConversationEntity
import com.bangersoul.aivance.core.database.model.AssistantMessageEntity
import com.bangersoul.aivance.core.domain.repository.AssistantConversation
import com.bangersoul.aivance.core.domain.repository.AssistantMessage
import com.bangersoul.aivance.core.domain.repository.AssistantRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssistantRepositoryImpl @Inject constructor(
    private val assistantDao: AssistantDao
) : AssistantRepository {

    override fun getConversations(): Flow<CoreResult<List<AssistantConversation>>> {
        return assistantDao.getConversations().map { entities ->
            runCatchingCore {
                entities.map { AssistantConversation(it.id, it.title, it.lastUpdatedAt) }
            }
        }
    }

    override fun getMessages(conversationId: String): Flow<CoreResult<List<AssistantMessage>>> {
        return assistantDao.getMessagesForConversation(conversationId).map { entities ->
            runCatchingCore {
                entities.map {
                    AssistantMessage(
                        id = it.id,
                        conversationId = it.conversationId,
                        role = it.role,
                        content = it.content,
                        timestamp = it.timestamp
                    )
                }
            }
        }
    }

    override suspend fun saveMessage(conversationId: String, role: String, content: String): CoreResult<Long> = runCatchingCore {
        // The messages table has a CASCADE foreign key to assistant_conversations
        // and Room runs with foreign_keys=ON, so the parent row MUST exist before
        // inserting a message — otherwise the insert throws SQLiteConstraintException.
        // Nothing else creates conversation rows, so ensure it here (idempotent:
        // IGNORE-on-conflict preserves an existing conversation's createdAt/title).
        val now = System.currentTimeMillis()
        assistantDao.insertConversationIfAbsent(
            AssistantConversationEntity(
                id = conversationId,
                title = defaultTitle(content),
                createdAt = now,
                lastUpdatedAt = now
            )
        )
        assistantDao.touchConversation(conversationId, now)
        assistantDao.insertMessage(
            AssistantMessageEntity(
                conversationId = conversationId,
                role = role,
                content = content
            )
        )
    }

    /** A short, human-readable title derived from the first message content. */
    private fun defaultTitle(content: String): String {
        val trimmed = content.trim().ifBlank { "Conversation" }
        return if (trimmed.length <= 40) trimmed else trimmed.take(40).trimEnd() + "…"
    }
}
