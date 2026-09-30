package com.bangersoul.aivance.feature.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.bangersoul.aivance.core.designsystem.theme.AivanceTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented dashboard rendering.
 *
 * The last two tests are the **UI half of the metric-integrity guard**: with zero data on screen,
 * no rated number and no scored surface may be rendered at all. The JVM half lives in
 * `DashboardZeroDataMetricGuardTest` and proves the state mapping; this proves the render.
 */
class DashboardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun scoredState() = DashboardUiState(
        isLoading = false,
        greeting = "Good Morning, Azmath",
        userDesignation = "Software Engineer",
        careerScore = 78,
        atsScore = 92,
        activeApplications = 5,
        savedJobs = 3,
        nextInterview = "Aug 12, 2026 · 10:00 AM",
        aiRecommendation = "Tailor your resume for senior roles.",
        graphInsights = CareerGraphInsightsUi(
            available = true,
            skillMatchPercent = 60,
            demonstratedSkillCount = 3,
            targetSkillCount = 5
        )
    )

    /** A clean install: nothing scored, nothing counted, no jobs to match against. */
    private fun zeroDataState() = DashboardUiState(
        isLoading = false,
        greeting = "Good Morning, Azmath",
        userDesignation = "",
        careerScore = null,
        atsScore = null,
        activeApplications = 0,
        savedJobs = 0,
        nextInterview = null,
        aiRecommendation = null,
        graphInsights = CareerGraphInsightsUi(
            available = true,
            skillMatchPercent = null,
            demonstratedSkillCount = 0,
            targetSkillCount = 0
        )
    )

    private fun renderContent(state: DashboardUiState) {
        composeTestRule.setContent {
            AivanceTheme {
                DashboardContent(
                    state = state,
                    onNavigateToResume = {},
                    onNavigateToJobs = {},
                    onNavigateToInterview = {},
                    onNavigateToTracker = {},
                    onNavigateToProfile = {},
                    onNavigateToAnalytics = {}
                )
            }
        }
    }

    private fun textIsRendered(text: String): Boolean =
        composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun dashboardDisplaysCareerScoreWhenScored() {
        renderContent(scoredState())

        composeTestRule.onNodeWithText("Career Score").assertIsDisplayed()
        composeTestRule.onNodeWithText("78").assertIsDisplayed()
        composeTestRule.onNodeWithText("Strong profile").assertIsDisplayed()
    }

    @Test
    fun dashboardDisplaysQuickStats() {
        renderContent(scoredState())

        composeTestRule.onNodeWithText("ATS Score").assertIsDisplayed()
        composeTestRule.onNodeWithText("92").assertIsDisplayed()
        composeTestRule.onNodeWithText("Active Apps").assertIsDisplayed()
        composeTestRule.onNodeWithText("Saved Jobs").assertIsDisplayed()
        composeTestRule.onNodeWithText("3").assertIsDisplayed()
    }

    @Test
    fun dashboardDisplaysSkillMatchWhenItIsMeasurable() {
        renderContent(scoredState())

        composeTestRule.onNodeWithText("Skill Match").assertIsDisplayed()
        composeTestRule.onNodeWithText("60%").assertIsDisplayed()
        composeTestRule.onNodeWithText("3 of 5 target-job skills demonstrated").assertIsDisplayed()
    }

    @Test
    fun zeroDataDashboardRendersNoRatedNumber() {
        renderContent(zeroDataState())

        // The metrics existed before this guard: 18 was the fabricated career composite and 100%
        // the unmeasured skill match. Neither may appear again.
        assertTrue("a career score must not be rendered at zero data", !textIsRendered("18"))
        assertTrue("skill match must not be rendered as 100% at zero data", !textIsRendered("100%"))
        // Counts are honest zeros and are still rendered ("Active Apps 0", "Saved Jobs 0");
        // what must never appear is a *rated* value, so no percentage may be rendered at all.
        assertTrue("no percentage belongs on a dashboard with nothing measured", !textIsRendered("0%"))
    }

    @Test
    fun zeroDataDashboardRendersNoCareerScoreSurface() {
        renderContent(zeroDataState())

        // The career-score card is gated on `state.careerScore != null`
        // (DashboardScreen.kt §2, AUDIT 06/34): a fresh account used to open on a
        // placeholder ring that also offered a third route into a career surface,
        // and unmeasured metrics now render nothing at all. This test used to
        // assert the *opposite* — that the placeholder and its "Unlock your
        // score" copy were on screen — and only now fails, because androidTest
        // had never been compiled against the current UI.
        //
        // So the guard is inverted: at zero data none of the scored surface may
        // appear. Do not "fix" this by restoring the placeholder.
        assertTrue(
            "the career-score ring must not render before anything is measured",
            !textIsRendered("Career Score")
        )
        assertTrue(
            "the unlock title must not render before anything is measured",
            !textIsRendered("Unlock your score")
        )
        assertTrue(
            "the unlock copy must not render before anything is measured",
            !textIsRendered("Upload a resume and run an ATS scan to unlock scoring.")
        )
        assertTrue(
            "the 'not scored yet' chip belongs to the card, which must be absent",
            !textIsRendered("Not scored yet")
        )

        // Skill Match is still shown, unmeasured, and asks for the input it needs
        // rather than celebrating a profile that has no target jobs.
        composeTestRule.onNodeWithText("Skill Match").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save target jobs to measure your skill match.").assertIsDisplayed()
    }
}
