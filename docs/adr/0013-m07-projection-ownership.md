# 13. M07 Career Projection Ownership & State Rehydration

Date: 2026-09-24

## Status

Accepted — architecture decision only. Entity/memory rehydration is **deferred**, not
implemented. Supersedes nothing; extends the M05 ownership model (ADR is additive).

## Context

The V2 Career Knowledge OS has reached:

- **M04-A** — versioned event payload contract (`schemaVersion`, `CareerEventContract`, codec).
- **M04-B** — durable, deterministic, idempotent replay of `career_event_log` into the graph's
  `CAREER_EVENT` provenance slice.
- **M04-C** — four flagship event payloads evolved to schema v2 carrying stable entity identity
  (`ResumeAnalysisCompleted` +`resumeId`/`versionId`, `JobSaved` +`jobId`,
  `ApplicationStageChanged` +`applicationId`, `InterviewCompleted` +`sessionId`); v1 rows still
  decode and remain non-rehydratable.
- **M05** — graph projection split into two disjoint, single-writer slices.
- **M06** — replay proven on a real Android Room runtime.

M04-C removed the *data* blocker for entity/memory rehydration (identity now reaches the log).
The remaining question — the subject of this ADR — is **ownership**: *may replay reconstruct
entity graph nodes (RESUME/JOB/APPLICATION/INTERVIEW_SESSION) and/or `career_memory_entries`, and
if so, who owns those rows?* This must be settled before any M07 implementation.

### Invariants that constrain the decision

1. Room DB remains the single authoritative source of application state.
2. `career_event_log` is append-only durable **audit** persistence — never canonical state.
3. **M05 single-writer rule:** the *entity projection* (every node type except `CAREER_EVENT`,
   plus all edges) is written exclusively by the live `CareerStateEngine` →
   `CareerGraphEngine.buildGraph` → `CareerGraphRepository.persist` →
   `GraphDao.replaceEntityProjection`. The *event-provenance projection* (`CAREER_EVENT` nodes
   only) is written exclusively by `CareerEventReplayEngine` →
   `CareerGraphRepository.replaceEventProjection` → `GraphDao.replaceNodesOfType`. Neither slice
   may erase the other.

## Traced current architecture (evidence)

Authoritative writers/readers, traced from source (not class names):

| State | Authoritative store | Writer | Graph projection writer |
|-------|--------------------|--------|-------------------------|
| Profile / Skills | `user_profiles` (Room) | `UserRepository` | `buildGraph` (PROFILE/SKILL) |
| Resumes / versions | `resumes` (Room) | `ResumeRepository` | `buildGraph` (RESUME/RESUME_VERSION) |
| Jobs / companies | job tables (Room) | `JobRepository` | `buildGraph` (JOB/COMPANY) |
| Applications | application tables (Room) | `ApplicationWorkflowRepository` | `buildGraph` (APPLICATION) |
| Interviews | interview tables (Room) | `InterviewRepository` | `buildGraph` (INTERVIEW_SESSION) |
| Career memory | `career_memory_entries` (Room) | `CareerMemoryRepository`/`CareerMemoryEngine` | — (not projected into graph today) |
| Event provenance | `career_event_log` (Room, append-only) | `CareerEventDispatcher` → `CareerEventLogRepository` | `CareerEventReplayEngine` (CAREER_EVENT) |

**Graph readers (critical finding).** The entity graph is effectively *write-only* in production
today. The only production reader of graph-node *contents* is `AiContextEngine2.buildContext`
(reads `graph.getNodesByType(SKILL)`), and `AiContextEngine2` has **no production caller**
(infrastructure-only). `CareerStateEngine` reads only `graph.nodes.size` / `edges.size` for count
telemetry. No ViewModel (`Dashboard`, `Jobs`, `JobDetails`, `Interview`, `Tracker`, `Assistant`)
queries graph node contents; they consume `CareerState` scalar fields derived from Room repos.
`CareerGraphRepository.loadGraph`, `analyzeSkillGaps`, and `getApplicationContext` have no
production callers. `CareerMemoryEngine` likewise has no production caller in `feature/`/`app/`.

**Payload sufficiency (critical finding).** Even v2 payloads carry only entity *identity*, not
full entity *attributes*. `JobSaved` v2 = `{jobId, company, title}` — no location, description,
remote flag, or provider that `buildGraph` puts on a JOB node. `ResumeAnalysisCompleted` v2 =
`{resumeId, versionId, atsScore}` — no resume name or version list. So the log alone still cannot
reconstruct an entity node *equivalent* to the one `buildGraph` produces from Room; it can only
assert "this id existed at this time." The log also records no deletions, so it cannot represent
removal of an entity.

## Decision

**Adopt Option C — the event log is historical *evidence*; Room stays authoritative; replay's
only graph output remains the `CAREER_EVENT` provenance projection.** Entity graph rehydration and
memory rehydration are **explicitly deferred** (not implemented) until BOTH preconditions hold:

1. **A real consumer contract exists** — a product feature that must display or reason over
   *reconstructed historical* entity/memory state distinct from current authoritative state. No
   such consumer exists today.
2. **Payloads are attribute-complete** — the event contract carries enough attributes (not just
   identity) to reconstruct a projection faithfully, or reconstruction is explicitly defined as a
   thin identity/provenance overlay reconciled against authoritative Room reads at query time.

Until then, the accepted ownership model is unchanged from M05:

```
Room entities ──► CareerStateEngine ──► CareerGraphEngine ──► replaceEntityProjection
                                                              (entity nodes + all edges)

career_event_log ──► CareerEventReplayEngine ──► replaceEventProjection
                                                  (CAREER_EVENT nodes only)

career_memory_entries ──► CareerMemoryEngine  (authoritative; no replay writer)
```

M04-C's benefit is realized without any ownership change: a v2 event's identity now flows into
its `CAREER_EVENT` provenance node's `payload.*` properties automatically, so a future
reconciliation read model can *join* provenance identity to authoritative Room entities without
replay ever writing an entity node.

### Rejected alternatives

**Option A — replay writes authoritative entity nodes.** Rejected. It puts a second writer into
the M05 entity slice, makes the append-only log a second source of truth, lets stale replay
overwrite newer authoritative state, and — because v2 payloads carry identity only, not full
attributes — cannot even produce a node equivalent to `buildGraph`'s. Violates invariants 1, 2,
and 3.

**Option B — a third, independently-owned "rehydrated entity" graph slice.** Rejected *as
premature*, not unsafe-by-construction. It would avoid the write conflict by giving replay its own
node types, but it manufactures a second representation of every entity with duplicate ids/edges,
a permanent reconciliation burden, and a real risk that a future consumer displays reconstructed
state as current state. Crucially, **no consumer needs it today** (the entity graph is write-only
in production), so building it would be speculative infrastructure. It remains the leading
candidate *if and when* precondition (1) materializes — but the decision to add a third slice must
be made against a concrete consumer contract, not in advance.

## Consequences

- **Pros:** one authoritative current-state representation is preserved; the log stays audit-only;
  no speculative code or schema is added; M04-C value (identity in provenance) is already
  delivered; the path to a reconciliation read model (Option C) or a justified third slice
  (Option B) stays open and is now documented.
- **Cons:** "state rehydration" as a user-facing capability is not delivered by M07; it is gated
  on a product decision. The audit log cannot, by itself, rebuild lost entity/memory state — a
  restore path would still come from Room backups, not replay.
- **Failure semantics unchanged:** replay still fails loudly and writes nothing on unknown type /
  unsupported version / malformed payload; the provenance slice rewrite stays transactional and
  idempotent.

## Verification plan (for any future M07 implementation)

If precondition (1) is met and Option B or a reconciliation read model is chosen, it must ship
with: deterministic `(timestamp, eventId)` ordering; stable node ids; idempotent repeated rebuild;
a dedicated owned slice that never touches the M05 entity slice or edges; explicit
"reconstructed vs authoritative" labeling for consumers; provenance (source eventId, timestamp,
schema version) on every reconstructed row; and Android-runtime proof (reuse the M06 `m06_test`
AVD, API 34 x86_64) covering rebuild-from-empty, rebuild-twice-idempotent, rebuild-after-live-
projection, and "replay does not overwrite newer authoritative state."

## Milestone verdict

**M07 ARCHITECTURE ACCEPTED** — Room authoritative + log-as-historical-evidence +
provenance-only replay. Entity/memory rehydration DEFERRED pending a consumer contract and
attribute-complete payloads. No production code change is justified by this decision.
