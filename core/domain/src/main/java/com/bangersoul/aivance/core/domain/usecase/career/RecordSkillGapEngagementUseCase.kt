package com.bangersoul.aivance.core.domain.usecase.career

import com.bangersoul.aivance.core.domain.repository.SkillGapEngagement
import com.bangersoul.aivance.core.domain.repository.SkillGapProgressRepository
import com.bangersoul.aivance.core.domain.usecase.UseCase
import javax.inject.Inject

data class RecordSkillGapEngagementRequest(
    val skill: String,
    val engagement: SkillGapEngagement
)

/**
 * Records that the user acted on a missing-skill chip (explored jobs or started
 * learning). Persisted so the dashboard can show durable momentum toward
 * closing each gap — distinct from graph-derived skill possession.
 */
class RecordSkillGapEngagementUseCase @Inject constructor(
    private val skillGapProgressRepository: SkillGapProgressRepository
) : UseCase<RecordSkillGapEngagementRequest, Unit>() {

    override suspend operator fun invoke(input: RecordSkillGapEngagementRequest) {
        skillGapProgressRepository.recordEngagement(input.skill, input.engagement)
    }
}
