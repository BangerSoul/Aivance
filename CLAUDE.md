# Mandatory Zero-Touch Pre-Flight Protocol (All Repository Contributions)

> [!IMPORTANT]
> **IRONCLAD INVARIANT**: BEFORE creating, modifying, editing, or deleting a single line of code, tests, or configuration in this repository, the model/agent MUST execute and pass the **7-Step Mandatory Pre-Flight Protocol**. Speculative edits, direct edits on protected branches, missing acceptance gates, and license negligence are STRICTLY FORBIDDEN.

## The 7 Mandatory Pre-Flight Gates

1. **Gate 1: License & Intellectual Property Audit**
   - Locate and inspect root `LICENSE`, `COPYING`, `NOTICE`, or `THIRD_PARTY_NOTICES.md`.
   - Identify the project license type (e.g. MIT, Apache-2.0, BSD, GPL, AGPL, Proprietary).
   - Enforce incompatible code prohibition: NEVER introduce code under more restrictive licenses (e.g., no GPL/AGPL in MIT/Apache repos without dual license).
   - Check if SPDX headers (`// SPDX-License-Identifier: MIT`) or DCO sign-offs (`Signed-off-by: Name <email>`) are required.

2. **Gate 2: Contribution Guidelines & Repository Standards Discovery**
   - Inspect `CONTRIBUTING.md`, `DEVELOPING.md`, `.github/PULL_REQUEST_TEMPLATE.md`, and style configs (`.editorconfig`, `.prettierrc`, ESLint, Ruff, rustfmt, Clippy).
   - Adhere strictly to the project's established conventions, tooling, and test runners.

3. **Gate 3: Heavy Builds & Cloud Compilation Assessment (Crave 20GB Boundary)**
   - Assess estimated total build & disk footprint (source tree, dependencies, intermediate build objects, output artifacts).
   - **>= 20 GB** (e.g., AOSP, Android ROMs, BlissOS, Linux kernels, Chromium/WebKit, full OS/guest images, massive monorepos): Local compilation is **STRICTLY FORBIDDEN**. Must immediately trigger cloud compilation via Crave: `crave.exe -n run -- "<cmd>"`.
   - **< 20 GB**: Local building and testing permitted.

4. **Gate 4: Acceptance Gates First Ledger (`GATES.md` & `unlazy`)**
   - **Write Gates First**: Before modifying any code, scaffold or update `GATES.md` (via `unlazy init solo` or `preflight init`).
   - Define observable, testable acceptance gates with runnable `CHECK:` commands and unambiguous `EXPECT:` tokens.
   - Run `unlazy lint GATES.md` (or `unlazy_lint`) to ensure cryptographic ledger validity.
   - Approve passing gates with `unlazy --approve GATES.md` and reverify zero regressions with `unlazy --reverify GATES.md` before claiming completion.

5. **Gate 5: Architectural Blueprint Alignment (BYOX Catalog)**
   - If constructing, refactoring, or recreating complex engines, protocols, or developer tools (git, redis, docker, web server, virtual dom, compilers, shells, databases, regex engines):
   - Query the BYOX blueprint catalog (`byox get <system>`) and follow the 5-Phase BYOX decomposition (Skeleton -> State Engine -> Protocol/Parser -> I/O -> Verification) rather than importing bloated black-box libraries.

6. **Gate 6: Version Control & Clean Branch Isolation**
   - Check `git status` for clean working tree.
   - **Never develop directly on `main`, `master`, or shared production branches**.
   - Create and checkout a structured topic branch (`feat/<name>`, `fix/<name>`, `refactor/<name>`, `docs/<name>`, `chore/<name>`).
   - Format commits according to Conventional Commits: `<type>(<scope>): <imperative summary>`.

7. **Gate 7: Automated Pre-Flight Certification Output**
   - Run `preflight check` (or emit the standard Pre-Flight Certification block) verifying all gates are satisfied.
   - If any gate fails, **HALT IMMEDIATELY** and resolve the gate before writing code.

---

# Build & Environment Instructions
- Crave CLI is in PATH (`crave.exe`). Always pass `-n` to avoid update prompt hangs.
- Never run heavy Android/AOSP/ROM or multi-hour compilation locally.
- Run builds remotely on Crave: `crave.exe -n run -- "<commands>"`
- Pull results locally: `crave.exe -n pull <path>`

<!-- BEGIN AI-SKILLS-REGISTRY MANAGED ROUTING -->
# AI Skills & MCP Tool Routing Matrix

Activate newly installed capabilities strictly based on task domain:

| Domain / Task Type | Assigned Capability | Platform | Key Policy |
| :--- | :--- | :--- | :--- |
| **Database & SQL** | `sqlite-inspector`, `postgres-connector`, `duckdb-analyzer`, `redis-manager` | Antigravity / Claude | Read-only queries by default. Verify schema first. |
| **Web Research** | `browser-playwright`, `browser-puppeteer`, `brave-search`, `fetch-markdown` | Antigravity / Claude | brave-search for queries; fetch for URLs. |
| **Video & Media** | `opencut`, `ffmpeg-media-tools`, `image-processor`, `audio-transcriber` | Antigravity / Claude | Read skill documentation before multi-step edit. |
| **Code & VCS** | `github-inspector`, `git-repo-tools`, `pyright-lsp`, `rust-analyzer-mcp` | Antigravity / Claude | Prefer built-in git; use MCP for deep AST queries. |
| **System & Ops** | `docker-container-mcp`, `filesystem-mcp`, `terminal-executor`, `kubernetes-mcp` | Antigravity / Claude | Strictly respect Crave cloud policies. |
| **Local LLM & Free Models** | `ollama` | Antigravity / Claude | Free offline completions, local embeddings, token-saving sub-tasks. |
| **Agent Runtimes & Harnesses** | `agent-harnesses` (MCP / Skill) | Antigravity / Claude | Query `recommend`, `pick_harness`, or `compare_for` when selecting agent frameworks, sandboxes, or autonomous workflows. |
| **Prompt Engineering & Optimization** | `prompt-optimizer` (MCP / Skill) | All AI Apps (Antigravity, Claude, Freebuff, Cursor, Codex) | Trigger with `/optimize`, `/prompt-optimizer`, or `@prompt-optimizer`. Refines user queries, constructs system prompts, iterates with feedback, and extracts variables. |

## Progressive Activation Guidelines:
1. On-Demand Loading: Do NOT load or inspect skill directories until user prompt triggers domain.
2. Safety First: Destructive database writes or system changes require explicit confirmation.
3. Cloud & Compilation: Route builds requiring 20 GB or more through Crave Devspace CLI (`crave.exe -n run`). Local compilation permitted only for < 20 GB.
4. Local Free Inference: Route routine code completion, local embeddings, or token-saving sub-tasks to local Ollama (`qwen2.5-coder:1.5b`, `http://localhost:11434` or `ollama` MCP tool).
5. Agent Frameworks & Harnesses: When evaluating, comparing, or bootstrapping AI agent runtimes, query the local `agent-harnesses` server (backed by `C:\Users\BangerSoul\Desktop\Projects\best-of-Agent-Harnesses\harnesses.json`) to inspect sandboxing, recovery tiers, and graveyard status.
6. Prompt Optimization & Meta-Prompting: When the user enters `/optimize`, `/prompt-optimizer`, `@prompt-optimizer`, or asks to refine/structure prompts, invoke `prompt-optimizer` MCP tools (`optimize_user_prompt`, `optimize_system_prompt`, `iterate_prompt`, `extract_prompt_variables`).

# CRITICAL DIRECTIVE: PROMPT OPTIMIZER INTERCEPTION & OPTIMIZE-FIRST PROTOCOL
When user input starts with or includes `/prompt-optimizer`, `/optimize`, `/system-prompt`, `/iterate-prompt`, or `@prompt-optimizer`:
1. **STRICT NON-EXECUTION & NON-INVESTIGATION**:
   - **DO NOT** execute, answer, investigate, or research the underlying question in the codebase.
   - **DO NOT** run bash, powershell, or git commands (e.g. `cd`, `grep`, `cat`).
   - **DO NOT** read source files (e.g. `AuthViewModel.kt`, `google-services.json`).
   - **NEVER** say "it's separable from the prompt-optimizer tooling, so let me just answer it directly from the code".
   - The user's input is **raw prompt text to be optimized**, NOT a task to be performed.
2. **DELIVER THE OPTIMIZED PROMPT FIRST**:
   - Immediately invoke the `prompt-optimizer` MCP tool (or apply the optimization template).
   - Output the optimized prompt inside a distinct Markdown code block:
     ```markdown
     [Optimized Prompt Content]
     ```
3. **CONFIRMATION BEFORE ANY ACTION**:
   - Immediately ask: *"Would you like me to execute this optimized prompt against your project now?"*
   - Stop and await user confirmation before touching files or running commands.
<!-- END AI-SKILLS-REGISTRY MANAGED ROUTING -->

# Autonomous Context Compaction & Memory Management Policy
- **Proactive Autocompact Trigger**:
  - Monitor token budget and context utilization continuously.
  - Proactively trigger autocompact (or execute /compact in Claude Code / Freebuff) whenever context utilization approaches ~70% capacity, or immediately upon completing a significant milestone, feature, or debug cycle.
  - Never allow sessions to degrade or hit token truncation limits before compressing state.
- **Compaction Retention Standard**:
  When compacting or checkpointing session state, retain ONLY:
  1. **Current Goal & Immediate Next Action**: 1-2 dense sentences defining the ongoing objective.
  2. **Permanent Invariants & Constraints**: Crave Cloud Compilation policy (crave.exe -n run), credential protections, and project paths.
  3. **Modified Files Ledger**: Bulleted list of modified file paths and their concise functional delta.
  4. **Active Blockers / Open Decisions**: Any unresolved questions or failing test IDs.
- **Aggressive Context Pruning**:
  - Discard historical command logs, raw compiler outputs, passed test runs, and conversational filler.
  - Never dump full file contents into context; use targeted slice views (StartLine/EndLine).
  - Summarize completed actions in 1-2 lines and discard the raw intermediate trial traces.
