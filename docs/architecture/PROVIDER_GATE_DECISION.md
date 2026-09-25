# Provider Gate — Decision Required (R4)

**Status:** decision pending. **No implementation performed.**
**Blocks:** R4 (provider/auth/onboarding enforcement), and the shape of R5 (navigation) and R7 (dormant V2 reconnection).

## Why this is a decision, not a fix

`onboardingCompleted` currently carries two different meanings at once:

| Meaning | Truthful value on a clean install |
|---|---|
| "The user finished the onboarding step flow" | set by `Skip All` (`OnboardingViewModel`) and, buggy, by `AuthViewModel.continueWithEmail` **before provider setup** |
| "The user has a working, validated provider" | **not tracked anywhere** |

So the app cannot distinguish "I deliberately chose to run without AI" from "I never got
around to configuring one" — and neither can the dashboard. The runtime evidence is
unambiguous: on a wiped install, onboarding's `Skip All` reaches Dashboard with **zero**
providers, and the auth path reaches it *without onboarding at all*.

Everything else in R4 follows from which of the two meanings the product intends. Both options
below fix the same latent bug (the auth path setting `onboardingCompleted = true`), and both
require splitting the flag; they differ in what the app does with the answer.

---

## Option A — Enforce a validated provider before Dashboard

**Contract:** Dashboard is unreachable until at least one provider is configured *and* validates.

| Aspect | Implication |
|---|---|
| **Journey** | `Splash → Welcome → Auth → Onboarding → AI provider (configure + validate) → job provider → Enrichment → Summary → Dashboard`. `Skip All` is removed or becomes "Configure a provider". |
| **Gate mechanics** | Two persisted flags: `onboardingCompleted` (step flow) **and** `providerConfigured` (derived from `ProviderManager.providerStatuses` ∈ {Healthy, Ready, Active}). The nav-graph root destination requires both. `AuthViewModel.continueWithEmail` stops setting `onboardingCompleted(true)`. |
| **UI** | Onboarding's Continue must explain *why* it is blocked and how to unblock (today validation failures are not surfaced as a reason). A provider-degraded banner is needed on Dashboard for the case where a provider was valid at setup and later is not. Settings needs a "fix provider" entry point. |
| **Offline users** | The on-device paths are the only way to satisfy the gate without a key: Gemma requires a **~2.9 GB** model download (with a ~271 MB low-storage variant) and Ollama requires a reachable local server. A user with neither a key nor the storage is **hard-locked out of the product**. This is the main risk and must be an accepted product decision, not a side effect. |
| **Tests** | Nav-gate unit tests asserting Dashboard is unreachable with zero validated providers; a clean-install instrumented test asserting the gate blocks and explains itself; the existing zero-data metric guard moves to a fixture that **has** a provider (zero data + zero provider stops being a reachable UI state); full journey E2E. |
| **Effort / risk** | Medium effort, **high** journey risk (it changes the primary path and can lock users out). |

---

## Option B — Explicit provider-optional mode

**Contract:** Dashboard is reachable by a deliberate, labeled choice that puts the app in an
acknowledged degraded mode with capability-level locks.

| Aspect | Implication |
|---|---|
| **Journey** | Same steps; `Skip All` becomes an explicit choice — *"Continue without AI — resume analysis, cover letters, interview generation and the assistant stay locked."* The user lands on Dashboard in **provider-optional mode**. |
| **Gate mechanics** | `onboardingCompleted` keeps its step-flow meaning; a new persisted `aiMode` (`CONFIGURED` / `OPTIONAL`) records the choice. Dashboard is allowed when `onboardingCompleted && (providerConfigured \|\| aiMode == OPTIONAL)`. `AuthViewModel` stops setting `onboardingCompleted(true)` (fixes the bypass). |
| **UI** | Capability locks instead of a wall: Resume analysis, Cover Letter generation, Interview question generation and Assistant render a "needs a provider" state with an inline CTA. A persistent, dismissible "AI is off" affordance in the Dashboard/Assistant header. Settings re-entry to configure later. |
| **Why now** | This option is only *honest* if the affected surfaces show real empty/locked states rather than substitute numbers. That prerequisite was just met: R3 removed the fabricated Career Score, Skill Match and readiness values, so a locked feature can now say "not measured" instead of inventing a figure. |
| **Tests** | Assert the two modes are distinguishable in the UI state; assert every provider-dependent action renders a locked state rather than failing silently; the existing zero-data metric guard stays valid as-is (provider-optional + zero data is a real, reachable state); clean-install test covers **both** the configure and the optional path. |
| **Effort / risk** | Medium effort, **low** journey risk (no lockout), larger surface area of "locked" affordances to get right. |

---

## Shared prerequisites (required under either option)

1. **Fix the auth bypass.** `AuthViewModel.continueWithEmail` sets `onboardingCompleted(true)`
   before any provider step, so a returning user can skip onboarding entirely. This is a defect
   under both options.
2. **Split the flag.** Whatever the policy, "finished the steps" and "has a working provider"
   must be separately observable, or no gate can be reasoned about.
3. **Make provider validation deterministic and explainable.** The gate is only as good as the
   validation it depends on, and today a failed validation does not tell the user why.

## Recommendation

**Option B.** It is the smaller change, it cannot lock a user out of their own data, and R3 has
just made the honest-empty-state story real enough to support it. Option A is the right choice
only if the product accepts that AI capability is a hard precondition for using it at all — in
which case the offline/no-key path needs a deliberate plan before enforcement ships.
