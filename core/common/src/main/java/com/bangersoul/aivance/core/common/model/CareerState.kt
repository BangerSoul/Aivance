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
    val lastEventTimestamp: Long = 0L
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
    val atsScore: Int = 0,
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
    val careerScore: Int = 0,
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
