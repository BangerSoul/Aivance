package com.bangersoul.aivance.core.domain.analytics

import com.bangersoul.aivance.core.common.model.AtsReport
import com.bangersoul.aivance.core.common.model.Recruiter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R3-1 — the composite career score is never fabricated.
 *
 * The regression this locks in: a brand-new install rendered a confident **18** on the
 * dashboard, because the composite always divided by four and the readiness slot defaulted to a
 * hardcoded `75`: `(0 + 0 + 0 + 75) / 4 = 18`.
 */
class CareerScoreEngineTest {

    private val engine = CareerScoreEngine()

    private fun ats(score: Int) = AtsReport(
        resumeVersionId = 1L,
        jobDescriptionId = 1L,
        overallScore = score,
        matchPercentage = score
    )

    private fun recruiters(count: Int) = (1..count).map { Recruiter(id = "r$it", name = "Recruiter $it", companyId = "1") }

    @Test
    fun `zero data produces no score instead of the 18 composite`() {
        val breakdown = engine.calculateCompositeScore(
            latestAtsReports = emptyList(),
            recruiters = emptyList(),
            applicationCount = 0,
            interviewReadiness = null
        )

        assertNull("an empty profile has no measurable career score", breakdown.overall)
        assertTrue("no dimension has evidence, so none is reported", breakdown.dimensions.isEmpty())
    }

    @Test
    fun `an unmeasured readiness is absent rather than defaulted`() {
        val breakdown = engine.calculateCompositeScore(
            latestAtsReports = emptyList(),
            recruiters = emptyList(),
            applicationCount = 0,
            interviewReadiness = null
        )

        // The fabricated 75 used to land here.
        assertNull(breakdown.dimension(CareerScoreEngine.DIM_INTERVIEW_READINESS))
        assertNull(breakdown.overall)
    }

    @Test
    fun `dimensions with no evidence are omitted, not scored zero`() {
        val breakdown = engine.calculateCompositeScore(
            latestAtsReports = emptyList(),
            recruiters = emptyList(),
            applicationCount = 2,
            interviewReadiness = null
        )

        assertNull(breakdown.dimension(CareerScoreEngine.DIM_ATS_READINESS))
        assertNull(breakdown.dimension(CareerScoreEngine.DIM_NETWORKING))
        assertNull(breakdown.dimension(CareerScoreEngine.DIM_INTERVIEW_READINESS))
        assertEquals("only consistency was measured", 4, breakdown.dimension(CareerScoreEngine.DIM_CONSISTENCY))
        assertEquals("the overall is the mean over measured dimensions only", 4, breakdown.overall)
    }

    @Test
    fun `overall is the mean of every evidence-backed dimension`() {
        val breakdown = engine.calculateCompositeScore(
            latestAtsReports = listOf(ats(80)),
            recruiters = recruiters(2), // 2 * 5 = 10
            applicationCount = 2, // 2 * 2 = 4
            interviewReadiness = 86
        )

        assertEquals(80, breakdown.dimension(CareerScoreEngine.DIM_ATS_READINESS))
        assertEquals(10, breakdown.dimension(CareerScoreEngine.DIM_NETWORKING))
        assertEquals(4, breakdown.dimension(CareerScoreEngine.DIM_CONSISTENCY))
        assertEquals(86, breakdown.dimension(CareerScoreEngine.DIM_INTERVIEW_READINESS))
        // (80 + 10 + 4 + 86) / 4 = 45
        assertEquals(45, breakdown.overall)
    }

    @Test
    fun `overall is measured as soon as a single dimension has evidence`() {
        val breakdown = engine.calculateCompositeScore(
            latestAtsReports = listOf(ats(90)),
            recruiters = emptyList(),
            applicationCount = 0,
            interviewReadiness = null
        )

        assertNotNull(breakdown.overall)
        assertEquals(90, breakdown.overall)
    }
}
