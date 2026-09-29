package com.bangersoul.aivance.core.domain.workflow

import com.bangersoul.aivance.core.common.events.ApplicationEvent
import com.bangersoul.aivance.core.common.events.CareerEvent
import com.bangersoul.aivance.core.common.events.CareerEventBus
import com.bangersoul.aivance.core.common.model.Application
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.domain.events.CareerEventDispatcher
import com.bangersoul.aivance.core.domain.repository.AnalyticsRepository
import com.bangersoul.aivance.core.domain.repository.ApplicationWorkflowRepository
import com.bangersoul.aivance.core.domain.repository.NotificationRepository
import com.bangersoul.aivance.core.domain.usecase.workflow.TaskGeneratorUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Proves the V2 career-event pipeline is actually wired to a real production
 * mutation: a successful application stage transition publishes a genuine
 * [ApplicationEvent.StageChanged] through the production [CareerEventDispatcher]
 * and onto the shared [CareerEventBus] that [CareerStateEngine] consumes.
 *
 * A real bus + real dispatcher are used (no bus stub), so the test proves the
 * end-to-end producer wiring rather than a direct bus call.
 */
class WorkflowEngineEventTest {

    private lateinit var repository: ApplicationWorkflowRepository
    private lateinit var analyticsRepository: AnalyticsRepository
    private lateinit var taskGenerator: TaskGeneratorUseCase
    private lateinit var eventBus: CareerEventBus
    private lateinit var dispatcher: CareerEventDispatcher
    private lateinit var engine: WorkflowEngine

    private val application = Application(
        id = 42L,
        jobId = 7L,
        currentStageId = "SAVED",
        status = "ACTIVE"
    )

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        analyticsRepository = mockk(relaxed = true)
        taskGenerator = mockk(relaxed = true)
        eventBus = CareerEventBus()
        dispatcher = CareerEventDispatcher(eventBus, mockk(relaxed = true))
        engine = WorkflowEngine(
            repository, analyticsRepository, taskGenerator, dispatcher,
            notificationRepository = mockk(relaxed = true)
        )

        coEvery { repository.saveApplication(any()) } returns Result.Success(42L)
        coEvery { repository.addTimelineEvent(any()) } returns Result.Success(1L)
        coEvery { taskGenerator(any()) } returns Result.Success(Unit)
        coEvery { analyticsRepository.createSnapshot() } returns Result.Success(1L)
    }

    @Test
    fun `successful stage transition publishes ApplicationStageChanged to the bus`() = runBlocking {
        val result = engine.transitionTo(application, "APPLIED")

        assertTrue("transition should succeed", result is Result.Success)

        val event = awaitEvent<ApplicationEvent.StageChanged>()
        assertEquals("42", event.applicationId)
        assertEquals("SAVED", event.oldStage)
        assertEquals("APPLIED", event.newStage)
    }

    @Test
    fun `no-op transition to the same stage publishes nothing`() = runBlocking {
        val result = engine.transitionTo(application, "SAVED")

        assertTrue("no-op transition should still succeed", result is Result.Success)

        // Give any (erroneous) async dispatch a chance to land, then assert none.
        val event = pollEvent<ApplicationEvent.StageChanged>(timeoutMs = 500)
        assertNull("a no-op transition must not emit a stage-change event", event)
    }

    private suspend inline fun <reified T : CareerEvent> awaitEvent(timeoutMs: Long = 2_000): T =
        pollEvent<T>(timeoutMs)
            ?: error("Expected a ${T::class.simpleName} event on the bus within ${timeoutMs}ms")

    /**
     * Polls the bus replay cache for an event of type [T]. Dispatch happens on
     * an internal Dispatchers.Default scope, so we poll rather than rely on the
     * test scheduler.
     */
    private suspend inline fun <reified T : CareerEvent> pollEvent(timeoutMs: Long): T? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val hit = eventBus.getRecentEvents().filterIsInstance<T>().firstOrNull()
            if (hit != null) return hit
            kotlinx.coroutines.delay(20)
        }
        return eventBus.getRecentEvents().filterIsInstance<T>().firstOrNull()
    }
}
