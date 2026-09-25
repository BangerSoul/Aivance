package com.bangersoul.aivance.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * All navigation destinations in the application.
 *
 * Layers:
 *  1. Gate — splash, welcome, auth, provider remediation (no tab bar)
 *  2. Workspaces — the five primary tabs, one independent backstack each
 *  3. Spokes — detail screens that live on a workspace backstack
 *  4. System — settings surfaces reachable from the Identity Hub
 */
@Serializable
sealed interface Destination : NavKey {
    val label: String

    // ── Layer 1: Authentication & Onboarding (Gate) ──────────────────────

    @Serializable
    data object Splash : Destination {
        override val label = "Splash"
    }

    @Serializable
    data object Welcome : Destination {
        override val label = "Welcome"
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

    /** Intelligence Hub — manages resumes and ATS analysis. */
    @Serializable
    data object Intelligence : Destination {
        override val label = "Intelligence"
    }

    /** Job Discovery — universal job search and market intelligence. */
    @Serializable
    data object Discovery : Destination {
        override val label = "Job Discovery"
    }

    /** Application Pipeline — Kanban workflow management. */
    @Serializable
    data object Pipeline : Destination {
        override val label = "Pipeline"
    }

    /** Prep Studio — Mock interviews and practice intelligence. */
    @Serializable
    data object PrepStudio : Destination {
        override val label = "Prep Studio"
    }

    // ── Layer 3: Secondary & Detail Screens ──────────────────────────────

    @Serializable
    data object Assistant : Destination {
        override val label = "Assistant"
    }

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

    /** Opens the Pipeline workspace with a job pre-selected (from a saved job). */
    @Serializable
    data class TrackApplication(val jobId: String) : Destination {
        override val label = "Pipeline"
    }

    /** Opens Job Discovery with the search pre-seeded to a skill (from a dashboard
     *  skill-gap chip), so the user sees roles demanding the skill they lack. */
    @Serializable
    data class DiscoverBySkill(val skill: String) : Destination {
        override val label = "Job Discovery"
    }

    /** Opens Prep Studio's Learn tab pre-seeded with a skill (from a dashboard
     *  skill-gap chip), so the user gets targeted learning recommendations. */
    @Serializable
    data class LearnSkill(val skill: String) : Destination {
        override val label = "Prep Studio"
    }

    // ── Layer 4: System ──────────────────────────────────────────────────

    @Serializable
    data object Appearance : Destination {
        override val label = "Appearance"
    }

    @Serializable
    data object ProviderManagement : Destination {
        override val label = "Providers"
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
         * Bottom-navigation tabs of the Main Career OS graph.
         * The loop: HQ -> Intel -> Discover -> Pipeline -> Practice.
         */
        val rootDestinations = listOf(
            Dashboard, Intelligence, Discovery, Pipeline, PrepStudio
        )

        val authenticatedDestinations = setOf(
            Dashboard, Intelligence, Discovery, Pipeline, PrepStudio,
            Assistant, Analytics, IdentityHub, About,
            ProviderManagement, Notifications, PrivacyCenter, Appearance,
            SavedJobs
        )

        val authDestinations = setOf(
            Splash, Welcome, Auth, ProviderSetup
        )
    }
}

/**
 * True when a destination lives inside the authenticated Main graph.
 */
fun Destination.isAuthenticatedDestination(): Boolean =
    this in Destination.authenticatedDestinations ||
        this is Destination.CompanyDetail ||
        this is Destination.ResumeDetail ||
        this is Destination.JobDetails ||
        this is Destination.RecruiterDashboard ||
        this is Destination.ApplyBrowser ||
        this is Destination.Ats ||
        this is Destination.CoverLetter ||
        this is Destination.ResumeEngine ||
        this is Destination.TrackApplication ||
        this is Destination.DiscoverBySkill ||
        this is Destination.LearnSkill

val Destination.icon: ImageVector?
    get() = when (this) {
        Destination.Splash,
        Destination.Welcome,
        Destination.Auth,
        Destination.ProviderSetup,
        is Destination.JobDetails,
        is Destination.ApplyBrowser,
        is Destination.CompanyDetail,
        is Destination.ResumeDetail -> null

        Destination.Dashboard -> Icons.Outlined.GridView
        Destination.Assistant -> Icons.Rounded.AutoAwesome
        Destination.Intelligence, is Destination.ResumeEngine -> Icons.Outlined.Description
        Destination.Discovery, is Destination.DiscoverBySkill -> Icons.Outlined.WorkOutline
        Destination.IdentityHub -> Icons.Outlined.PersonOutline
        is Destination.Ats -> Icons.Outlined.Assessment
        is Destination.CoverLetter -> Icons.Outlined.Assignment
        is Destination.RecruiterDashboard -> Icons.Rounded.PersonSearch
        Destination.SavedJobs -> Icons.Rounded.BookmarkBorder
        Destination.Pipeline, is Destination.TrackApplication -> Icons.Outlined.ViewKanban
        is Destination.LearnSkill, Destination.PrepStudio -> Icons.Outlined.School
        Destination.Appearance -> Icons.Rounded.Palette
        Destination.ProviderManagement -> Icons.Rounded.Tune
        Destination.Notifications -> Icons.Rounded.Notifications
        Destination.PrivacyCenter -> Icons.Rounded.PrivacyTip
        Destination.Analytics -> Icons.Rounded.BarChart
        Destination.About -> Icons.Rounded.Info
        Destination.Resources -> Icons.Rounded.MenuBook
    }

/**
 * Localized label resource for each destination.
 */
val Destination.labelRes: Int
    @StringRes get() = when (this) {
        Destination.Splash -> R.string.dest_splash
        Destination.Welcome -> R.string.dest_welcome
        Destination.Dashboard -> R.string.dest_dashboard
        Destination.Assistant -> R.string.dest_assistant
        Destination.Intelligence, is Destination.ResumeEngine -> R.string.dest_intelligence
        Destination.Discovery, is Destination.DiscoverBySkill -> R.string.dest_discovery
        Destination.IdentityHub -> R.string.dest_profile
        is Destination.Ats -> R.string.dest_ats
        is Destination.CoverLetter -> R.string.dest_cover_letter
        is Destination.JobDetails -> R.string.dest_job_details
        is Destination.ApplyBrowser -> R.string.dest_apply
        is Destination.RecruiterDashboard -> R.string.dest_recruiter_discovery
        Destination.SavedJobs -> R.string.dest_saved_jobs
        Destination.Pipeline, is Destination.TrackApplication -> R.string.dest_pipeline
        is Destination.LearnSkill, Destination.PrepStudio -> R.string.dest_prep_studio
        Destination.Appearance -> R.string.dest_appearance
        Destination.ProviderManagement -> R.string.dest_providers
        Destination.Notifications -> R.string.dest_notifications
        Destination.PrivacyCenter -> R.string.dest_privacy
        Destination.Auth -> R.string.dest_sign_in
        Destination.ProviderSetup -> R.string.dest_provider_setup
        is Destination.CompanyDetail -> R.string.dest_company
        is Destination.ResumeDetail -> R.string.dest_resume_detail
        Destination.Analytics -> R.string.dest_analytics
        Destination.About -> R.string.dest_about
        Destination.Resources -> R.string.dest_resources
    }
