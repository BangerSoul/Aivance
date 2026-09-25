package com.bangersoul.aivance.core.data.repository

import com.bangersoul.aivance.core.datastore.PreferencesManager
import com.bangersoul.aivance.core.domain.repository.SkillGapEngagement
import com.bangersoul.aivance.core.domain.repository.SkillGapProgress
import com.bangersoul.aivance.core.domain.repository.SkillGapProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed [SkillGapProgressRepository].
 *
 * Serializes the whole engagement map into a single string preference. The set
 * is expected to stay tiny (a handful of skills a user has acted on), so a
 * compact line-delimited encoding is simpler and cheaper than a dedicated Room
 * table — and it keeps this engagement signal deliberately separate from the
 * authoritative graph/entity persistence.
 *
 * Encoding: `skillKey|engagementOrdinal|updatedAtMillis` per line.
 */
@Singleton
class SkillGapProgressRepositoryImpl @Inject constructor(
    private val preferencesManager: PreferencesManager
) : SkillGapProgressRepository {

    override fun observeProgress(): Flow<List<SkillGapProgress>> =
        preferencesManager.getStringFlow(KEY_PROGRESS, "").map(::decode)

    override suspend fun recordEngagement(skillKey: String, engagement: SkillGapEngagement) {
        val normalized = skillKey.lowercase().trim()
        if (normalized.isEmpty() || engagement == SkillGapEngagement.NONE) return

        val current = decode(preferencesManager.getString(KEY_PROGRESS, "")).associateBy { it.skillKey }
        val existing = current[normalized]

        // Monotonic: never downgrade a stronger engagement with a later weaker one.
        val nextLevel = maxOf(existing?.engagement?.ordinal ?: 0, engagement.ordinal)
        val merged = current.toMutableMap()
        merged[normalized] = SkillGapProgress(
            skillKey = normalized,
            engagement = SkillGapEngagement.entries[nextLevel],
            updatedAt = System.currentTimeMillis()
        )

        preferencesManager.putString(KEY_PROGRESS, encode(merged.values))
    }

    private fun encode(items: Collection<SkillGapProgress>): String =
        items.joinToString("\n") { "${it.skillKey}|${it.engagement.ordinal}|${it.updatedAt}" }

    private fun decode(raw: String): List<SkillGapProgress> {
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val parts = line.split('|')
            if (parts.size != 3) return@mapNotNull null
            val key = parts[0].takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val ordinal = parts[1].toIntOrNull()?.takeIf { it in SkillGapEngagement.entries.indices }
                ?: return@mapNotNull null
            val updatedAt = parts[2].toLongOrNull() ?: return@mapNotNull null
            SkillGapProgress(
                skillKey = key,
                engagement = SkillGapEngagement.entries[ordinal],
                updatedAt = updatedAt
            )
        }.toList()
    }

    private companion object {
        const val KEY_PROGRESS = "skill_gap_progress_v1"
    }
}
