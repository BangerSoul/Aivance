# UESF Skill Taxonomy v1.0.0

Every skill in the Ultimate Engineering Skills Framework belongs to exactly **one primary category**, identified by a two-letter code embedded in its skill ID (`uesf-<code>-<slug>`). Categories are deliberately **cross-referenced**, not duplicated: a skill may list related skills and dependencies across categories, but its home is singular. This keeps the taxonomy a clean, acyclic partitioning of engineering capability.

## Design principles

1. **Singular primary category.** One home per skill prevents classification ambiguity and makes discovery deterministic.
2. **Codes are stable.** Category codes never change once shipped; a category may be *extended* (new subcategories) but never re-keyed, so skill IDs remain stable.
3. **Extensible.** New categories are added through a taxonomy RFC (see `policies/contribution.md`) — a one-page proposal reviewed by the governance skill. Adding a category never requires touching existing skills.
4. **Machine-enforced.** The validator (`tools/validate_framework.py`) rejects any skill whose category code is not in this table.
5. **Discovery via `related_skills` + `dependencies`.** Cross-cutting concerns (e.g., "accessibility in UI work") are expressed as links, not as duplicate skills.

## Master table

| Code | Category | Scope |
|------|----------|-------|
| `co` | Core | Language- and domain-agnostic engineering primitives every workflow composes (plan, implement, test, debug, review, refactor). |
| `pl` | Planning | Decomposition, estimation, sequencing, plan authoring and execution. |
| `ar` | Architecture | Solution design, system decomposition, trade-off analysis, ADRs. |
| `ra` | Repository Intelligence | Codebase mapping, dependency analysis, ownership, risk surface. |
| `im` | Implementation | Writing and integrating production code. |
| `te` | Testing | Unit, integration, regression, and E2E verification design. |
| `dg` | Debugging | Root-cause analysis, bisection, instrumentation. |
| `rf` | Refactoring | Behavior-preserving restructuring. |
| `pf` | Performance | Profiling, optimization, capacity and load reasoning. |
| `se` | Security | Threat modeling, audit, dependency scanning, hardening. |
| `ax` | Accessibility | WCAG conformance, inclusive design verification. |
| `do` | Documentation | API docs, guides, runbooks, changelogs. |
| `re` | Release Engineering | Versioning, packaging, release trains, rollback strategies. |
| `de` | DevOps | CI/CD, infrastructure as code, automation pipelines. |
| `cl` | Cloud | Cloud-native architecture and managed services. |
| `ai` | AI Engineering | Agent systems, model integration, RAG, evaluation. |
| `pe` | Prompt Engineering | Instruction design, prompt evaluation, model-agnostic prompting. |
| `ux` | UX | Research, flows, information architecture, usability. |
| `ui` | UI | Interface implementation, design systems, frontend craft. |
| `da` | Data | Modeling, pipelines, quality, privacy. |
| `nw` | Networking | Diagnostics, protocol reasoning, distributed-systems behavior. |
| `au` | Automation | Scripting, tooling, workflow automation. |
| `rs` | Research | Web research, synthesis, evidence-backed decision support. |
| `ce` | Certification | Audit and certification of code, systems, and framework assets. |
| `pm` | Product Management | Specs, scoping, prioritization, MVP reasoning. |
| `gv` | Project Governance | Milestones, risk, progress tracking, meeting artifacts. |
| `le` | Continuous Learning | Skill discovery, marketplace intake, framework evolution. |
| `mk` | Meta Skills | Skills about skills: generating, validating, optimizing, merging, benchmarking, versioning. |
| `km` | Knowledge Management | Knowledge graphs, indexes, retrieval of organizational knowledge. |
| `ac` | Agent Collaboration | Multi-agent orchestration, hand-offs, role design. |
| `ex` | Example | Demonstrations and worked examples of the skill format. |

## Skills registered in v1.0.0

| Skill ID | Category | Kind |
|----------|----------|------|
| `uesf-co-planning` | `co` | core |
| `uesf-co-implementation` | `co` | core |
| `uesf-co-testing` | `co` | core |
| `uesf-co-debugging` | `co` | core |
| `uesf-co-review` | `co` | core |
| `uesf-co-refactoring` | `co` | core |
| `uesf-ar-solution-architecture` | `ar` | engineering |
| `uesf-ra-repository-analysis` | `ra` | engineering |
| `uesf-se-security-audit` | `se` | engineering |
| `uesf-pf-performance-optimization` | `pf` | engineering |
| `uesf-ax-accessibility-audit` | `ax` | engineering |
| `uesf-do-documentation` | `do` | engineering |
| `uesf-re-release-engineering` | `re` | engineering |
| `uesf-de-devops-automation` | `de` | engineering |
| `uesf-da-data-modeling` | `da` | engineering |
| `uesf-rs-research-synthesis` | `rs` | engineering |
| `uesf-ce-certification-audit` | `ce` | engineering |
| `uesf-gv-project-governance` | `gv` | engineering |
| `uesf-le-continuous-learning` | `le` | engineering |
| `uesf-pe-prompt-engineering` | `pe` | ai |
| `uesf-ai-agent-design` | `ai` | ai |
| `uesf-ai-evaluation` | `ai` | ai |
| `uesf-ai-rag-systems` | `ai` | ai |
| `uesf-ai-model-integration` | `ai` | ai |
| `uesf-ux-ux-audit` | `ux` | ux |
| `uesf-ui-ui-implementation` | `ui` | ui |
| `uesf-mk-repository-analyzer` | `mk` | meta |
| `uesf-mk-skill-generator` | `mk` | meta |
| `uesf-mk-skill-optimizer` | `mk` | meta |
| `uesf-mk-skill-refactorer` | `mk` | meta |
| `uesf-mk-skill-merger` | `mk` | meta |
| `uesf-mk-skill-validator` | `mk` | meta |
| `uesf-mk-skill-reviewer` | `mk` | meta |
| `uesf-mk-skill-benchmarker` | `mk` | meta |
| `uesf-mk-skill-version-manager` | `mk` | meta |
| `uesf-mk-skill-dependency-resolver` | `mk` | meta |
| `uesf-mk-skill-doc-generator` | `mk` | meta |
| `uesf-mk-skill-test-generator` | `mk` | meta |
| `uesf-mk-skill-certification-engine` | `mk` | meta |
| `uesf-ex-hello-world` | `ex` | example |
| `uesf-ex-api-design-review` | `ex` | example |

## Category lifecycle

- **Propose:** draft a one-page RFC with name, code, scope, and at least one candidate skill.
- **Review:** governance skill (`uesf-gv-project-governance`) checks overlap with existing categories and the 5-year evolution plan.
- **Land:** update this file, the validator `TAXONOMY` table, and the JSON Schema enum in one commit. Old codes are never removed; they may be marked `retired`.

## Relationship to other UESF documents

- Knowledge graph: `docs/knowledge-graph.md` (concepts and pattern flow)
- Dependency graph: `docs/dependency-graph.md` (skill-to-skill edges, machine-generated)
- Spec standard: `docs/skill-spec.md`
