package com.bangersoul.aivance.core.common.model

import kotlinx.serialization.Serializable

@Serializable
data class CareerState(
    val profile: ProfileState = ProfileState(),
    val intelligence: IntelligenceState = IntelligenceState(),
    val discovery: DiscoveryState = DiscoveryState(),
    val pipeline: PipelineState = PipelineState(),
    val growth: GrowthState = GrowthState(),
    val recommendations: List<CareerRecommendation> = emptyList(),
    val nextBestAction: CareerRecommendation? = null,
    val lifecycleStage: CareerLifecycleStage = CareerLifecycleStage.ONBOARDING,
    val intelligenceHub: CareerIntelligence? = null,
    val graphNodeCount: Int = 0,
    val graphEdgeCount: Int = 0,
    val lastEventTimestamp: Long = 0L,
    /**
     * Revision of the graph projection that has been *durably persisted*.
     *
     * Graph persistence runs off the state transformation (R2), so a state can be built
     * before its projection lands in the store. The writer bumps this only after a real
     * write, which (a) makes the post-write state structurally distinct so it is always
     * delivered, letting graph readers re-read a slice that is now guaranteed fresh, and
     * (b) terminates after one extra emission because the re-projected graph is
     * content-identical and is never re-persisted.
     */
    val graphRevision: Long = 0L
)

@Serializable
data class ProfileState(
    val name: String = "",
    val targetRole: String = "",
    val skills: List<String> = emptyList(),
    val completionPercentage: Int = 0,
    val workPreference: String = "REMOTE",
    val salaryExpectation: String = "",
    val visaRequired: Boolean = false
)

@Serializable
data class IntelligenceState(
    val latestResumeId: Long? = null,
    /**
     * Latest measured ATS score, or `null` when no ATS report exists yet.
     *
     * Nullable on purpose (R3-1): the dashboard used to present the `0` floor as a real
     * "ATS Score", which reads as a measured result even though no analysis had ever run.
     */
    val atsScore: Int? = null,
    val lastScanDate: Long? = null,
    val totalResumes: Int = 0
)

@Serializable
data class DiscoveryState(
    val savedJobsCount: Int = 0,
    val lastSearchQuery: String? = null,
    val matchingJobsCount: Int = 0
)

@Serializable
data class PipelineState(
    val activeApplications: Int = 0,
    val upcomingInterviews: List<UpcomingInterviewShort> = emptyList(),
    val pipelineDistribution: Map<String, Int> = emptyMap()
)

@Serializable
data class GrowthState(
    /**
     * Composite career score, or `null` when no dimension has been measured yet.
     *
     * Nullable on purpose (R3-1): a score of `18` used to be rendered for a brand-new install
     * because a hardcoded readiness default was fed into the composite.
     */
    val careerScore: Int? = null,
    val weeklyApplicationCount: Int = 0,
    val topStrengths: List<String> = emptyList(),
    val keyBlockers: List<String> = emptyList()
)

@Serializable
data class UpcomingInterviewShort(
    val id: String,
    val company: String,
    val role: String,
    val dateTime: String
)

@Serializable
enum class CareerLifecycleStage {
    ONBOARDING,
    PREPARING,
    OPTIMIZING,
    EXPLORING,
    APPLYING,
    INTERVIEWING,
    STRATEGIZING
}
