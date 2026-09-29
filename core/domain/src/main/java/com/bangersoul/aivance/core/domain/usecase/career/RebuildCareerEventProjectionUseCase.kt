package com.bangersoul.aivance.core.domain.usecase.career

import com.bangersoul.aivance.core.domain.replay.CareerEventReplayEngine
import com.bangersoul.aivance.core.domain.replay.CareerReplayResult
import com.bangersoul.aivance.core.domain.usecase.NoInputUseCase
import javax.inject.Inject

/**
 * Deliberate maintenance/repair trigger for durable event replay (V2-M05).
 *
 * This is the single production entry point that activates
 * [CareerEventReplayEngine.replayAll], rebuilding the graph's `CAREER_EVENT` provenance slice from
 * the durable `career_event_log`. It exists so replay is a conscious repair operation invoked from
 * a maintenance boundary rather than an accidental hot-path side effect.
 *
 * ### Explicitly NOT a hot-path operation
 *
 * Replay must never run from ordinary state calculation, repository reads, or per-event dispatch.
 * In particular it is deliberately NOT wired into
 * [com.bangersoul.aivance.core.domain.engine.CareerStateEngine.state], which recomputes on every
 * data change. Callers should invoke this only from an intentional maintenance/repair context
 * (e.g. a diagnostics action or a one-shot integrity-repair worker).
 *
 * ### Safety
 *
 * The heavy lifting lives in the replay engine: deterministic `(timestamp, eventId)` ordering,
 * idempotent `event_<eventId>` node ids, loud failure on any undecodable event, and a single
 * transactional slice rewrite that leaves the entity projection untouched (M05 ownership split).
 * This use case adds no logic of its own beyond being the explicit trigger, so it inherits every
 * one of those guarantees.
 *
 * @return the [CareerReplayResult] so a caller can surface success (with counts) or the explicit
 *   first blocking failure for diagnostics.
 */
class RebuildCareerEventProjectionUseCase @Inject constructor(
    private val replayEngine: CareerEventReplayEngine
) : NoInputUseCase<CareerReplayResult>() {

    override suspend fun invoke(): CareerReplayResult = replayEngine.replayAll()
}
