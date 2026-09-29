package com.bangersoul.aivance.core.data.repository

import com.bangersoul.aivance.core.common.model.NotificationItem
import com.bangersoul.aivance.core.common.model.NotificationType
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.common.result.runCatchingCore
import com.bangersoul.aivance.core.database.dao.NotificationDao
import com.bangersoul.aivance.core.database.model.NotificationEntity
import com.bangersoul.aivance.core.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [NotificationRepository] over the v29 `notifications` table.
 *
 * Read side maps rows to [NotificationItem]s, defensively decoding the stored
 * type name — a row persisted by a future producer with an unknown type name
 * renders as GENERAL instead of crashing the inbox. Write side wraps each
 * mutation in [runCatchingCore] so a full disk surfaces as a failure value
 * rather than an exception through the caller's worker/ViewModel.
 */
@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val notificationDao: NotificationDao
) : NotificationRepository {

    override fun getNotifications(): Flow<List<NotificationItem>> =
        notificationDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeUnreadCount(): Flow<Int> = notificationDao.observeUnreadCount()

    override suspend fun record(notification: NotificationItem): Result<Unit> = runCatchingCore {
        notificationDao.upsert(notification.toEntity())
    }

    override suspend fun markAsRead(id: String): Result<Unit> = runCatchingCore {
        notificationDao.markRead(id)
    }

    override suspend fun markAllAsRead(): Result<Unit> = runCatchingCore {
        notificationDao.markAllRead()
    }

    override suspend fun delete(id: String): Result<Unit> = runCatchingCore {
        notificationDao.delete(id)
    }

    override suspend fun record(
        id: String,
        type: NotificationType,
        title: String,
        message: String,
        timestamp: Long
    ): Result<Unit> = record(
        NotificationItem(
            id = id,
            title = title,
            message = message,
            timestamp = timestamp,
            isRead = false,
            type = type
        )
    )

    private fun NotificationEntity.toDomain(): NotificationItem = NotificationItem(
        id = id,
        title = title,
        message = message,
        timestamp = timestamp,
        isRead = isRead,
        type = NotificationType.entries.firstOrNull { it.name == type } ?: NotificationType.GENERAL
    )

    private fun NotificationItem.toEntity(): NotificationEntity = NotificationEntity(
        id = id,
        title = title,
        message = message,
        timestamp = timestamp,
        isRead = isRead,
        type = type.name
    )
}
