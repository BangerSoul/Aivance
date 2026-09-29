package com.bangersoul.aivance.core.common.events

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class CareerEventBusTest {

    private lateinit var eventBus: CareerEventBus

    @Before
    fun setUp() {
        eventBus = CareerEventBus(replayCacheSize = 64, extraBufferCapacity = 64)
    }

    // ========================================================================
    // 1. Emission and Subscription Tests
    // ========================================================================

    @Test
    fun `emit dispatches event to active subscriber`() = runTest {
        val collected = mutableListOf<CareerEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            eventBus.events.collect { collected.add(it) }
        }

        val event = ResumeEvent.Created(resumeId = "res-101", name = "Senior Android Engineer")
        eventBus.emit(event)

        assertEquals(1, collected.size)
        assertEquals(event, collected.first())

        job.cancel()
    }

    @Test
    fun `tryEmit dispatches event immediately to active subscribers`() = runTest {
        val collected = mutableListOf<CareerEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            eventBus.events.collect { collected.add(it) }
        }

        val event = JobEvent.Saved(jobId = "job-42", company = "Google", title = "Staff Engineer")
        val emitted = eventBus.tryEmit(event)

        assertTrue(emitted)
        assertEquals(1, collected.size)
        assertEquals(event, collected.first())

        job.cancel()
    }

    @Test
    fun `multiple subscribers all receive emitted events`() = runTest {
        val subscriber1 = mutableListOf<CareerEvent>()
        val subscriber2 = mutableListOf<CareerEvent>()

        val job1 = launch(UnconfinedTestDispatcher(testScheduler)) {
            eventBus.events.collect { subscriber1.add(it) }
        }
        val job2 = launch(UnconfinedTestDispatcher(testScheduler)) {
            eventBus.events.collect { subscriber2.add(it) }
        }

        val event1 = ResumeEvent.Deleted(resumeId = "res-1")
        val event2 = JobEvent.Archived(jobId = "job-1")

        eventBus.emit(event1)
        eventBus.emit(event2)

        assertEquals(listOf(event1, event2), subscriber1)
        assertEquals(listOf(event1, event2), subscriber2)

        job1.cancel()
        job2.cancel()
    }

    // ========================================================================
    // 2. Typed Event Filtering (eventsOfType and convenience flows)
    // ========================================================================

    @Test
    fun `eventsOfType filters correctly for AtsEvent`() = runTest {
        val atsEvents = mutableListOf<AtsEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            eventBus.eventsOfType<AtsEvent>().collect { atsEvents.add(it) }
        }

        val resumeEvent = ResumeEvent.Created(resumeId = "res-1", name = "Resume")
        val atsScoreEvent = AtsEvent.ScoreChanged(
            resumeVersionId = "ver-1",
            jobId = "job-1",
            overallScore = 88,
            matchedKeywords = listOf("Kotlin", "Coroutines"),
            missingKeywords = listOf("GraphQL")
        )
        val jobEvent = JobEvent.Saved(jobId = "job-1", company = "Acme", title = "Dev")
        val atsOptEvent = AtsEvent.OptimizationCompleted(resumeVersionId = "ver-1", jobId = "job-1", tipsGeneratedCount = 3)
        val appEvent = ApplicationEvent.Created(applicationId = "app-1", jobId = "job-1", company = "Acme")

        eventBus.emit(resumeEvent)
        eventBus.emit(atsScoreEvent)
        eventBus.emit(jobEvent)
        eventBus.emit(atsOptEvent)
        eventBus.emit(appEvent)

        assertEquals(2, atsEvents.size)
        assertEquals(atsScoreEvent, atsEvents[0])
        assertEquals(atsOptEvent, atsEvents[1])

        job.cancel()
    }

    @Test
    fun `convenience subscription flows filter their respective event families`() = runTest {
        val collectedResumes = mutableListOf<ResumeEvent>()
        val collectedAts = mutableListOf<AtsEvent>()
        val collectedJobs = mutableListOf<JobEvent>()
        val collectedApps = mutableListOf<ApplicationEvent>()
        val collectedInterviews = mutableListOf<InterviewEvent>()
        val collectedSkills = mutableListOf<SkillEvent>()
        val collectedGoals = mutableListOf<GoalEvent>()
        val collectedAutomation = mutableListOf<AutomationEvent>()

        val j1 = launch(UnconfinedTestDispatcher(testScheduler)) { eventBus.resumeEvents().collect { collectedResumes.add(it) } }
        val j2 = launch(UnconfinedTestDispatcher(testScheduler)) { eventBus.atsEvents().collect { collectedAts.add(it) } }
        val j3 = launch(UnconfinedTestDispatcher(testScheduler)) { eventBus.jobEvents().collect { collectedJobs.add(it) } }
        val j4 = launch(UnconfinedTestDispatcher(testScheduler)) { eventBus.applicationEvents().collect { collectedApps.add(it) } }
        val j5 = launch(UnconfinedTestDispatcher(testScheduler)) { eventBus.interviewEvents().collect { collectedInterviews.add(it) } }
        val j6 = launch(UnconfinedTestDispatcher(testScheduler)) { eventBus.skillEvents().collect { collectedSkills.add(it) } }
        val j7 = launch(UnconfinedTestDispatcher(testScheduler)) { eventBus.goalEvents().collect { collectedGoals.add(it) } }
        val j8 = launch(UnconfinedTestDispatcher(testScheduler)) { eventBus.automationEvents().collect { collectedAutomation.add(it) } }

        val eResume = ResumeEvent.Updated(resumeId = "r1", versionId = "v1")
        val eAts = AtsEvent.OptimizationCompleted(resumeVersionId = "v1", jobId = "j1", tipsGeneratedCount = 3)
        val eJob = JobEvent.Applied(jobId = "j1", applicationId = "a1")
        val eApp = ApplicationEvent.StageChanged(applicationId = "a1", oldStage = "APPLIED", newStage = "INTERVIEW")
        val eInterview = InterviewEvent.Started(sessionId = "s1", role = "Android Lead", company = "Google")
        val eSkill = SkillEvent.Detected(skillName = "Kotlin", sourceEntity = "Resume", confidence = 0.95f)
        val eGoal = GoalEvent.ProgressUpdated(goalId = "g1", percentComplete = 75)
        val eAuto = AutomationEvent.RuleTriggered(ruleId = "rule-1", actionType = "NOTIFY", payload = mapOf("msg" to "Stage updated"))

        eventBus.emit(eResume)
        eventBus.emit(eAts)
        eventBus.emit(eJob)
        eventBus.emit(eApp)
        eventBus.emit(eInterview)
        eventBus.emit(eSkill)
        eventBus.emit(eGoal)
        eventBus.emit(eAuto)

        assertEquals(listOf<ResumeEvent>(eResume), collectedResumes)
        assertEquals(listOf<AtsEvent>(eAts), collectedAts)
        assertEquals(listOf<JobEvent>(eJob), collectedJobs)
        assertEquals(listOf<ApplicationEvent>(eApp), collectedApps)
        assertEquals(listOf<InterviewEvent>(eInterview), collectedInterviews)
        assertEquals(listOf<SkillEvent>(eSkill), collectedSkills)
        assertEquals(listOf<GoalEvent>(eGoal), collectedGoals)
        assertEquals(listOf<AutomationEvent>(eAuto), collectedAutomation)

        listOf(j1, j2, j3, j4, j5, j6, j7, j8).forEach { it.cancel() }
    }

    // ========================================================================
    // 3. Replay Cache Retention and getRecentEvents Tests
    // ========================================================================

    @Test
    fun `replay cache retains recent events for new subscribers`() = runTest {
        val bus = CareerEventBus(replayCacheSize = 3, extraBufferCapacity = 0)

        val e1 = ResumeEvent.Created(resumeId = "1", name = "First")
        val e2 = ResumeEvent.Created(resumeId = "2", name = "Second")
        val e3 = ResumeEvent.Created(resumeId = "3", name = "Third")
        val e4 = ResumeEvent.Created(resumeId = "4", name = "Fourth")
        val e5 = ResumeEvent.Created(resumeId = "5", name = "Fifth")

        bus.emit(e1)
        bus.emit(e2)
        bus.emit(e3)
        bus.emit(e4)
        bus.emit(e5)

        // New subscriber joins after emission
        val lateCollected = mutableListOf<CareerEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            bus.events.collect { lateCollected.add(it) }
        }

        // Must receive only the latest 3 events due to replayCacheSize = 3
        assertEquals(3, lateCollected.size)
        assertEquals(listOf(e3, e4, e5), lateCollected)

        job.cancel()
    }

    @Test
    fun `getRecentEvents queries replay cache correctly`() = runTest {
        val bus = CareerEventBus(replayCacheSize = 5, extraBufferCapacity = 0)

        val events = (1..5).map { ResumeEvent.Created(resumeId = "$it", name = "Resume $it") }
        events.forEach { bus.emit(it) }

        assertEquals(events.takeLast(3), bus.getRecentEvents(3))
        assertEquals(listOf(events.last()), bus.getRecentEvents(1))
        assertEquals(events, bus.getRecentEvents(5))
        assertEquals(events, bus.getRecentEvents(10)) // requested > available, returns all available
        assertTrue(bus.getRecentEvents(0).isEmpty())
        assertTrue(bus.getRecentEvents(-1).isEmpty())
    }

    @Test
    fun `resetReplayCache clears buffered events for subsequent subscribers`() = runTest {
        val bus = CareerEventBus(replayCacheSize = 5)
        bus.emit(ResumeEvent.Created(resumeId = "1", name = "Resume 1"))
        bus.emit(ResumeEvent.Created(resumeId = "2", name = "Resume 2"))

        assertEquals(2, bus.getRecentEvents().size)

        bus.resetReplayCache()

        assertEquals(0, bus.getRecentEvents().size)

        val lateCollected = mutableListOf<CareerEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            bus.events.collect { lateCollected.add(it) }
        }

        assertTrue(lateCollected.isEmpty())
        job.cancel()
    }

    // ========================================================================
    // 4. Concurrent Emission Tests
    // ========================================================================

    @Test
    fun `concurrent emission across multiple coroutines emits without dropping or deadlocking`() = runTest {
        val bus = CareerEventBus(replayCacheSize = 100, extraBufferCapacity = 200)
        val eventCount = 100
        val receivedCount = AtomicInteger(0)

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            bus.events.collect {
                receivedCount.incrementAndGet()
            }
        }

        coroutineScope {
            val jobs = (1..eventCount).map { index ->
                launch(Dispatchers.Default) {
                    bus.emit(ResumeEvent.Created(resumeId = "res-$index", name = "Engineer $index"))
                }
            }
            jobs.joinAll()
        }

        assertEquals(eventCount, receivedCount.get())
        job.cancel()
    }

    // ========================================================================
    // 5. CareerEventListener and Subscribe Helper Tests
    // ========================================================================

    @Test
    fun `CareerEventListener receives dispatched events`() = runTest {
        val received = mutableListOf<CareerEvent>()
        val listener = CareerEventListener { event ->
            received.add(event)
        }

        val job = eventBus.subscribe(
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            listener = listener
        )

        val event = GoalEvent.Created(goalId = "g-1", targetRole = "Principal Engineer")
        eventBus.emit(event)

        assertEquals(1, received.size)
        assertEquals(event, received.first())

        job.cancel()
    }

    @Test
    fun `typed subscribe extension receives only specified event type`() = runTest {
        val interviewEvents = mutableListOf<InterviewEvent>()

        val job = eventBus.subscribe<InterviewEvent>(
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        ) { event ->
            interviewEvents.add(event)
        }

        eventBus.emit(ResumeEvent.Created(resumeId = "res-1", name = "Test"))
        val turnEvent = InterviewEvent.TurnEvaluated(
            sessionId = "session-1",
            turnIndex = 1,
            scoreClarity = 9,
            scoreAccuracy = 10
        )
        eventBus.emit(turnEvent)
        eventBus.emit(JobEvent.Archived(jobId = "job-1"))

        assertEquals(1, interviewEvents.size)
        assertEquals(turnEvent, interviewEvents.first())

        job.cancel()
    }

    // ========================================================================
    // 6. Event Metadata and ID Overload Tests
    // ========================================================================

    @Test
    fun `event metadata generates unique eventId and captures timestamp`() {
        val e1 = ResumeEvent.Created(resumeId = "1", name = "A")
        val e2 = ResumeEvent.Created(resumeId = "1", name = "A")

        assertNotNull(e1.eventId)
        assertNotNull(e2.eventId)
        assertTrue(e1.eventId.isNotBlank())
        assertTrue(e1.eventId != e2.eventId) // distinct UUIDs
        assertTrue(e1.timestamp > 0)
    }

    @Test
    fun `correlationId is preserved when supplied`() {
        val correlationId = "corr-xyz-789"
        val event = ApplicationEvent.Created(
            applicationId = "app-1",
            jobId = "job-1",
            company = "Netflix",
            correlationId = correlationId
        )
        assertEquals(correlationId, event.correlationId)
    }

    @Test
    fun `Long ID secondary constructors correctly stringify IDs`() {
        val resumeEvent = ResumeEvent.Created(resumeId = 123L, name = "Resume Long")
        assertEquals("123", resumeEvent.resumeId)

        val atsEvent = AtsEvent.ScoreChanged(
            resumeVersionId = 456L,
            jobId = 789L,
            overallScore = 95
        )
        assertEquals("456", atsEvent.resumeVersionId)
        assertEquals("789", atsEvent.jobId)

        val jobEvent = JobEvent.Saved(jobId = 999L, company = "Meta", title = "E6")
        assertEquals("999", jobEvent.jobId)

        val appEvent = ApplicationEvent.TaskCompleted(applicationId = 101L, taskId = 202L)
        assertEquals("101", appEvent.applicationId)
        assertEquals("202", appEvent.taskId)
    }
}
