package com.bangersoul.aivance.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.bangersoul.aivance.core.designsystem.icon.AiNavIcons
import com.bangersoul.aivance.core.designsystem.icon.DestinationIconIntent
import com.bangersoul.aivance.core.designsystem.icon.IconVariant
import kotlinx.serialization.Serializable

/**
 * All navigation destinations in the application.
 *
 * Layers:
 *  1. Gate — splash, welcome, auth, provider remediation (no tab bar)
 *  2. Workspaces — the four primary tabs plus the AI orb, one backstack each
 *  3. Spokes — detail screens that live on a workspace backstack
 *  4. System — settings surfaces reachable from the Identity Hub
 *
 * Subtraction-first (AUDIT §3.2): the legacy aliases `Intelligence`,
 * `PrepStudio`, `Assistant`, `TrackApplication`, `DiscoverBySkill` and
 * `LearnSkill` are gone. Their seeds are carried as nav arguments on the
 * canonical workspace they used to fork into, so every tab still owns exactly
 * one backstack *and* the seed survives process death (a `remember` seed does
 * not).
 */
@Serializable
sealed interface Destination : NavKey {
    val label: String

    // ── Layer 1: Authentication & Onboarding (Gate) ──────────────────────

    @Serializable
    data object Splash : Destination {
        override val label = "Splash"
    }

    /** Sign-in / create-account screen. */
    @Serializable
    data object Auth : Destination {
        override val label = "Sign In"
    }

    /**
     * Provider configuration flow — the single onboarding surface. Reachable
     * from first launch AND as mid-session remediation when the provider
     * contract becomes unconfigured/invalid.
     */
    @Serializable
    data object ProviderSetup : Destination {
        override val label = "Provider Setup"
    }

    // ── Layer 2: Primary Navigation (The Core Loop) ──────────────────────

    @Serializable
    data object Dashboard : Destination {
        override val label = "Dashboard"
    }

    /**
     * Job Discovery — universal job search and market intelligence.
     *
     * @param query optional search seed. Replaces the deleted `DiscoverBySkill`
     *  spoke: a skill-gap chip deep-links straight into the Discovery tab.
     */
    @Serializable
    data class Discovery(val query: String? = null) : Destination {
        override val label = "Job Discovery"
    }

    /**
     * Application Pipeline — Kanban workflow management.
     *
     * @param jobId optional job to pre-select. Replaces the deleted
     *  `TrackApplication` spoke, which pushed a *second* Tracker screen onto the
     *  Pipeline stack (a back-stack double entry).
     */
    @Serializable
    data class Pipeline(val jobId: String? = null) : Destination {
        override val label = "Pipeline"
    }

    /**
     * Studio workspace (N1) — the merged Intelligence + Prep Studio tab with
     * segmented sub-tabs (Resumes · Practice).
     *
     * @param segment the active sub-tab. Replaces the deleted `Intelligence`
     *  (RESUMES) and `PrepStudio` (PRACTICE) roots.
     * @param learnSkill optional skill to pre-seed the Learn surface with.
     *  Replaces the deleted `LearnSkill` spoke.
     */
    @Serializable
    data class Studio(
        val segment: StudioSegment = StudioSegment.RESUMES,
        val learnSkill: String? = null
    ) : Destination {
        override val label = "Studio"
    }

    /**
     * AI orb tab (N1) — the Assistant elevated to a permanent nav surface.
     * Rendered by the nav shell as the aurora orb, never a standard tab item,
     * and never a root destination: it owns a dedicated backstack so a
     * conversation survives workspace switches.
     */
    @Serializable
    data object AssistantOrb : Destination {
        override val label = "Assistant"
    }

    // ── Layer 3: Secondary & Detail Screens ──────────────────────────────

    @Serializable
    data object Analytics : Destination {
        override val label = "Analytics"
    }

    @Serializable
    data object IdentityHub : Destination {
        override val label = "Identity Hub"
    }

    /** Resume Engine — carries an optional preloaded job description so a saved
     *  job can jump straight into a tailored-resume flow (ATS scan JD pre-filled). */
    @Serializable
    data class ResumeEngine(val jobDescription: String? = null) : Destination {
        override val label = "Resume Engine"
    }

    @Serializable
    data class Ats(
        val jobDescription: String? = null,
        /** When set, opens the ATS screen directly on this saved report. */
        val reportId: Long? = null
    ) : Destination {
        override val label = "ATS Scanner"
    }

    @Serializable
    data class CoverLetter(val jobId: Long? = null) : Destination {
        override val label = "Cover Letter"
    }

    @Serializable
    data class JobDetails(val jobId: String) : Destination {
        override val label = "Job Details"
    }

    @Serializable
    data class CompanyDetail(val companyId: String) : Destination {
        override val label = "Company"
    }

    @Serializable
    data class ResumeDetail(val resumeId: Long) : Destination {
        override val label = "Resume Detail"
    }

    @Serializable
    data class RecruiterDashboard(val jobId: String) : Destination {
        override val label = "Recruiter Discovery"
    }

    /**
     * In-app apply surface — hosts the real external apply page in a WebView
     * alongside an AI suggestions panel (ATS score, cover letter, recruiter
     * emails) so the user never has to leave the app to apply.
     */
    @Serializable
    data class ApplyBrowser(val jobId: String) : Destination {
        override val label = "Apply"
    }

    @Serializable
    data object SavedJobs : Destination {
        override val label = "Saved Jobs"
    }

    // ── Layer 4: System ──────────────────────────────────────────────────

    @Serializable
    data object Appearance : Destination {
        override val label = "Appearance"
    }

    @Serializable
    data object Notifications : Destination {
        override val label = "Notifications"
    }

    @Serializable
    data object PrivacyCenter : Destination {
        override val label = "Privacy & Security"
    }

    @Serializable
    data object About : Destination {
        override val label = "About"
    }

    @Serializable
    data object Resources : Destination {
        override val label = "Resources"
    }

    companion object {
        /**
         * Bottom-navigation tabs of the Main Career OS graph (N1):
         * HQ -> Discover -> Pipeline -> Studio, with the AI orb (Assistant)
         * inserted between HQ and the workspaces by the nav shell.
         *
         * These are the canonical instances. Every seeded variant
         * (`Studio(PRACTICE)`, `Discovery(query)`, `Pipeline(jobId)`) resolves
         * onto the backstack of the tab it belongs to.
         */
        val rootDestinations = listOf(
            Dashboard, Discovery(), Pipeline(), Studio()
        )

        /** Every destination that owns a workspace backstack — the four N1 tabs. */
        val workspaceDestinations = rootDestinations

        val authenticatedDestinations = setOf(
            Dashboard, Discovery(), Pipeline(), Studio(), AssistantOrb, Analytics,
            IdentityHub, About, Notifications, PrivacyCenter,
            Appearance, Resources, SavedJobs
        )

        val authDestinations = setOf(
            Splash, Auth, ProviderSetup
        )
    }
}

/**
 * True when a destination lives inside the authenticated Main graph.
 *
 * `Discovery`/`Pipeline`/`Studio` are parameterised, so a *seeded* instance is
 * not equal to its canonical set member; they are matched by type. `Resources`
 * is in the set too — without it a deep link could push an authenticated
 * surface onto the auth backstack before sign-in (AUDIT §3.2 guard gap).
 */
fun Destination.isAuthenticatedDestination(): Boolean =
    this in Destination.authenticatedDestinations ||
        this is Destination.Discovery ||
        this is Destination.Pipeline ||
        this is Destination.Studio ||
        this is Destination.CompanyDetail ||
        this is Destination.ResumeDetail ||
        this is Destination.JobDetails ||
        this is Destination.RecruiterDashboard ||
        this is Destination.ApplyBrowser ||
        this is Destination.Ats ||
        this is Destination.CoverLetter ||
        this is Destination.ResumeEngine

/**
 * I3 icon intent — paired outlined/filled variants so the nav shell can render
 * a duotone selected state. Workspaces use the custom 1.7dp AiNavIcons set;
 * secondary surfaces pair Material outlined/filled icons; gate and detail
 * surfaces carry no vector (empty intent).
 */
val Destination.iconIntent: DestinationIconIntent
    get() = when (this) {
        Destination.Splash,
        Destination.Auth,
        Destination.ProviderSetup,
        is Destination.JobDetails,
        is Destination.ApplyBrowser,
        is Destination.CompanyDetail,
        is Destination.ResumeDetail,
        Destination.AssistantOrb -> DestinationIconIntent()

        Destination.Dashboard -> DestinationIconIntent(
            outlined = AiNavIcons.DashboardOutlined,
            filled = AiNavIcons.DashboardFilled
        )
        is Destination.Studio -> DestinationIconIntent(
            outlined = Icons.Outlined.Description,
            filled = Icons.Filled.Description
        )
        is Destination.ResumeEngine -> DestinationIconIntent(
            outlined = Icons.Outlined.Description,
            filled = Icons.Filled.Description
        )
        is Destination.Discovery -> DestinationIconIntent(
            outlined = AiNavIcons.DiscoveryOutlined,
            filled = AiNavIcons.DiscoveryFilled
        )
        Destination.IdentityHub -> DestinationIconIntent(
            outlined = Icons.Outlined.PersonOutline,
            filled = Icons.Filled.Person
        )
        is Destination.Ats -> DestinationIconIntent(
            outlined = Icons.Outlined.Assessment,
            filled = Icons.Filled.Assessment
        )
        is Destination.CoverLetter -> DestinationIconIntent(
            outlined = Icons.Outlined.Assignment,
            filled = Icons.Filled.Assignment
        )
        is Destination.RecruiterDashboard -> DestinationIconIntent(
            outlined = Icons.Rounded.PersonSearch,
            filled = Icons.Filled.PersonSearch
        )
        Destination.SavedJobs -> DestinationIconIntent(
            outlined = Icons.Rounded.BookmarkBorder,
            filled = Icons.Filled.Bookmark
        )
        is Destination.Pipeline -> DestinationIconIntent(
            outlined = AiNavIcons.PipelineOutlined,
            filled = AiNavIcons.PipelineFilled
        )
        Destination.Appearance -> DestinationIconIntent(
            outlined = Icons.Rounded.Palette,
            filled = Icons.Filled.Palette
        )
        Destination.Notifications -> DestinationIconIntent(
            outlined = Icons.Rounded.Notifications,
            filled = Icons.Filled.Notifications
        )
        Destination.PrivacyCenter -> DestinationIconIntent(
            outlined = Icons.Rounded.PrivacyTip,
            filled = Icons.Filled.PrivacyTip
        )
        Destination.Analytics -> DestinationIconIntent(
            outlined = Icons.Rounded.BarChart,
            filled = Icons.Filled.BarChart
        )
        Destination.About -> DestinationIconIntent(
            outlined = Icons.Rounded.Info,
            filled = Icons.Filled.Info
        )
        Destination.Resources -> DestinationIconIntent(
            outlined = Icons.Rounded.MenuBook,
            filled = Icons.Filled.MenuBook
        )
    }

/**
 * Legacy single-icon accessor for call sites without a selection state
 * (tests, secondary chrome). Resolves the outlined variant.
 */
val Destination.icon: ImageVector?
    get() = iconIntent.forVariant(IconVariant.OUTLINED)

/**
 * Localized label resource for each destination.
 */
val Destination.labelRes: Int
    @StringRes get() = when (this) {
        Destination.Splash -> R.string.dest_splash
        Destination.Dashboard -> R.string.dest_dashboard
        is Destination.Studio -> R.string.dest_studio
        is Destination.ResumeEngine -> R.string.dest_intelligence
        is Destination.Discovery -> R.string.dest_discovery
        Destination.IdentityHub -> R.string.dest_profile
        is Destination.Ats -> R.string.dest_ats
        is Destination.CoverLetter -> R.string.dest_cover_letter
        is Destination.JobDetails -> R.string.dest_job_details
        is Destination.ApplyBrowser -> R.string.dest_apply
        is Destination.RecruiterDashboard -> R.string.dest_recruiter_discovery
        Destination.SavedJobs -> R.string.dest_saved_jobs
        is Destination.Pipeline -> R.string.dest_pipeline
        Destination.Appearance -> R.string.dest_appearance
        Destination.Notifications -> R.string.dest_notifications
        Destination.PrivacyCenter -> R.string.dest_privacy
        Destination.Auth -> R.string.dest_sign_in
        Destination.ProviderSetup -> R.string.dest_provider_setup
        is Destination.CompanyDetail -> R.string.dest_company
        is Destination.ResumeDetail -> R.string.dest_resume_detail
        Destination.Analytics -> R.string.dest_analytics
        Destination.About -> R.string.dest_about
        Destination.Resources -> R.string.dest_resources
        Destination.AssistantOrb -> R.string.dest_assistant
    }
