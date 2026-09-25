package com.bangersoul.aivance.core.domain.analytics

import com.bangersoul.aivance.core.common.model.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * R2 metric-integrity contract for [KPIEngine]: an undefined ratio (0/0) must be reported as
 * `null` (not-enough-data), never as a literal measured 0%.
 */
class KPIEngineTest {

    private val engine = KPIEngine()

    private fun app(stageId: String) = Application(jobId = 1L, currentStageId = stageId)

    @Test
    fun `interview rate is null when nothing has been applied`() {
        assertNull(engine.calculateInterviewRate(emptyList()))
        // Only bookmarked (SAVED) jobs: still nothing applied -> undefined ratio.
        assertNull(engine.calculateInterviewRate(listOf(app("SAVED"), app("SAVED"))))
    }

    @Test
    fun `interview rate counts interview and offer stages over applied`() {
        val apps = listOf(
            app("SAVED"),       // excluded from the denominator (not applied)
            app("APPLIED"),
            app("INTERVIEW"),
            app("OFFER")
        )
        // 2 of 3 applied reached interview-or-beyond.
        assertEquals(66.66, engine.calculateInterviewRate(apps)!!, 0.01)
    }

    @Test
    fun `legacy INTERVIEWING alias still counts as an interview`() {
        val apps = listOf(app("APPLIED"), app("INTERVIEWING"))
        assertEquals(50.0, engine.calculateInterviewRate(apps)!!, 0.001)
    }

    @Test
    fun `offer rate is null when nothing has been applied`() {
        assertNull(engine.calculateOfferRate(emptyList()))
        assertNull(engine.calculateOfferRate(listOf(app("SAVED"))))
    }

    @Test
    fun `offer rate counts offers over applied`() {
        val apps = listOf(app("APPLIED"), app("INTERVIEW"), app("OFFER"), app("OFFER"))
        // 2 of 4 applied reached an offer.
        assertEquals(50.0, engine.calculateOfferRate(apps)!!, 0.001)
    }

    @Test
    fun `conversion rate is null for an empty pipeline`() {
        assertNull(engine.calculateConversionRate(emptyList(), "OFFER"))
    }

    @Test
    fun `conversion rate is a fraction of the whole pipeline`() {
        val apps = listOf(app("SAVED"), app("OFFER"))
        assertEquals(50.0, engine.calculateConversionRate(apps, "OFFER")!!, 0.001)
    }
}
