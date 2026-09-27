package com.bangersoul.aivance.core.data.repository

import com.bangersoul.aivance.core.common.model.NotificationItem
import com.bangersoul.aivance.core.common.model.NotificationType
import com.bangersoul.aivance.core.database.dao.NotificationDao
import com.bangersoul.aivance.core.database.model.NotificationEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit coverage for [NotificationRepositoryImpl] over a fake [NotificationDao]:
 * recording maps the domain model into a durable row, mutations translate to DAO
 * calls, and the read side decodes defensively (unknown type → GENERAL).
 */
class NotificationRepositoryImplTest {

    private class FakeNotificationDao : NotificationDao {
        val rows = LinkedHashMap<String, NotificationEntity>()

        override suspend fun upsert(notification: NotificationEntity) {
            rows[notification.id] = notification
        }

        override fun observeAll() = kotlinx.coroutines.flow.flow {
            emit(rows.values.sortedByDescending { it.timestamp })
        }

        override fun observeUnreadCount() = kotlinx.coroutines.flow.flow {
            emit(rows.values.count { !it.isRead })
        }

        override suspend fun markRead(id: String) {
            rows[id]?.let { rows[id] = it.copy(isRead = true) }
        }

        override suspend fun markAllRead() {
            rows.keys.forEach { id -> rows[id] = rows.getValue(id).copy(isRead = true) }
        }

        override suspend fun delete(id: String) {
            rows.remove(id)
        }

        override suspend fun clear() = rows.clear()
    }

    private val dao = FakeNotificationDao()
    private val repository = NotificationRepositoryImpl(dao)

    @Test
    fun `record persists the full notification`() = runTest {
        repository.record(
            id = "job_alert_periodic",
            type = NotificationType.JOB_ALERT,
            title = "New job matches",
            message = "3 new matching jobs",
            timestamp = 1_000L
        )

        val row = dao.rows.getValue("job_alert_periodic")
        assertEquals("New job matches", row.title)
        assertEquals("JOB_ALERT", row.type)
        assertEquals(false, row.isRead)
    }

    @Test
    fun `record with the same id upserts in place`() = runTest {
        repository.record("n1", NotificationType.GENERAL, "First", "m")
        repository.record("n1", NotificationType.GENERAL, "Second", "m")

        assertEquals(1, dao.rows.size)
        assertEquals("Second", dao.rows.getValue("n1").title)
    }

    @Test
    fun `getNotifications emits newest-first domain items`() = runTest {
        repository.record("old", NotificationType.GENERAL, "Old", "m", timestamp = 100L)
        repository.record("new", NotificationType.INTERVIEW_REMINDER, "New", "m", timestamp = 200L)

        val items = repository.getNotifications().first()

        assertEquals(listOf("new", "old"), items.map { it.id })
        assertEquals(NotificationType.INTERVIEW_REMINDER, items.first().type)
    }

    @Test
    fun `markAsRead and markAllAsRead persist read state`() = runTest {
        repository.record("a", NotificationType.GENERAL, "A", "m")
        repository.record("b", NotificationType.GENERAL, "B", "m")

        repository.markAsRead("a")
        assertEquals(true, dao.rows.getValue("a").isRead)
        assertEquals(false, dao.rows.getValue("b").isRead)

        repository.markAllAsRead()
        assertTrue(dao.rows.values.all { it.isRead })
    }

    @Test
    fun `delete removes only the targeted row`() = runTest {
        repository.record("a", NotificationType.GENERAL, "A", "m")
        repository.record("b", NotificationType.GENERAL, "B", "m")

        repository.delete("a")

        assertEquals(setOf("b"), dao.rows.keys)
    }

    @Test
    fun `unknown persisted type decodes to GENERAL instead of crashing`() = runTest {
        dao.upsert(
            NotificationEntity(
                id = "future",
                title = "t",
                message = "m",
                timestamp = 1L,
                isRead = false,
                type = "SOME_FUTURE_TYPE"
            )
        )

        val item = repository.getNotifications().first().single()
        assertEquals(NotificationType.GENERAL, item.type)
    }

    @Test
    fun `unread count reflects persisted read state`() = runTest {
        repository.record("a", NotificationType.GENERAL, "A", "m")
        repository.record("b", NotificationType.GENERAL, "B", "m")

        repository.markAsRead("a")

        assertEquals(1, repository.observeUnreadCount().first())
    }
}
