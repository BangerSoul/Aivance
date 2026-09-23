# AiVance V2 — Master Security Audit & Defense-in-Depth Review

**Document Type:** Principal Security Audit & Vulnerability Assessment  
**Target Repository:** `IamAzmathullaShaikh/Aivance`  
**Security Lead:** Principal Security Architect & Lead AppSec Engineer  
**Status:** Canonical Security Baseline (OWASP Mobile Top 10 Compliant)  

---

## 1. Executive Security Summary

An exhaustive security audit was conducted across the AiVance codebase spanning credential persistence, cryptographic key management, local database encryption, network transport security, prompt injection defenses, and agent capability controls.

### Security Scorecard
* **Credential Storage**: 🟢 PASS (Hardware-backed Android Keystore + Google Tink AEAD)
* **Local Database**: 🟢 PASS (SQLCipher encryption + Room cipher integration)
* **Network Transport**: 🟢 PASS (TLS 1.3 enforcement + SHA-256 Certificate Pinning)
* **Prompt Injection**: 🟢 PASS (Active regex heuristic filter + sanitization in `AgentSafetyPolicy`)
* **AI Context Leakage**: 🟢 PASS (Deterministic PII redactor in `AiContextEngine2`)
* **Agent Capabilities**: 🟢 PASS (Mandatory `HumanApprovalGate` for external & destructive actions)
* **IPC & App Components**: 🟢 PASS (No exported components without permissions; secure `FileProvider`)

---

## 2. Deep Dive: Subsystem Audit

### 2.1 API Key Storage & Hardware Keystore
* **Implementation**: Verified in `core:datastore` (`EncryptedUserPreferencesSerializer.kt`) and `core:sdk` (`SecretManager.kt`).
* **Mechanism**: Cryptographic keys are generated in the hardware-backed **Android Keystore** (StrongBox where available, TEE otherwise). Keys never leave secure hardware in plaintext.
* **Cipher**: Google Tink AES256-GCM authenticated encryption with associated data (AEAD).
* **Finding**: Zero plaintext API keys or provider secrets are stored in standard SharedPreferences or SQLite tables.

### 2.2 Local Database Encryption
* **Implementation**: `AivanceDatabase` in `core:database`.
* **Mechanism**: Integrated SQLCipher 256-bit AES database encryption.
* **Finding**: Relational tables containing resume content, application notes, and recruiter communication history cannot be read via ADB backup or physical storage inspection on unrooted or rooted devices without the Keystore-wrapped key.

### 2.3 Network Security & TLS Pinning
* **Implementation**: Verified in `core:network` (`CertificatePins.kt`) and `JobProvidersModule.kt`.
* **Pins Enforced**:
  - `api.anthropic.com`
  - `generativelanguage.googleapis.com`
  - `api.groq.com`
  - `api.openai.com`
  - `api.apify.com`
* **Protocol**: TLS 1.3 enforced as minimum protocol version. Cleartext HTTP traffic is disabled globally in Android Manifest via `android:usesCleartextTraffic="false"`.

### 2.4 Prompt Injection & Untrusted Content Sanitization
* **Implementation**: Verified in `core:domain:agent` (`AgentSafetyPolicy.kt`).
* **Defenses**:
  - Rejection of prompt override heuristics (`ignore previous instructions`, `system prompt override`, `reveal all api keys`).
  - Active HTML/script tag stripping on untrusted job descriptions and incoming recruiter messages (`<script>`, SQL injection keywords).
  - Loop and runaway plan threshold gating (max 30 steps, max 3 repeated actions).

### 2.5 AI Context Leakage & PII Redaction
* **Implementation**: Verified in `core:domain:context` (`AiContextEngine2.kt`).
* **Defenses**:
  - Automatic regex redaction of candidate email addresses (`[USER_EMAIL]`), phone numbers (`[USER_PHONE]`), and tax/SSN identifiers (`[USER_ID]`) before feeding into prompt contexts.
  - Strict token budgeting prevents inadvertent dumping of entire local databases into AI model prompts.

### 2.6 Android App Components & Attack Surface
* **`AndroidManifest.xml`**:
  - Zero unexported activities, services, or broadcast receivers exposed to external apps without signature permissions.
  - `FileProvider`: Rooted strictly in app-internal cache directory (`${applicationId}.fileprovider`) with `grantUriPermissions="true"`.
  - WebViews: WebViews in the application disable JavaScript execution unless explicitly required for OAuth flow, with file access (`setAllowFileAccess(false)`) and universal file access disabled.

---

## 3. Threat Model & Invariant Protections

| Threat Vector | Potential Impact | AiVance V2 Protective Invariant |
| :--- | :--- | :--- |
| **Malicious Job Description** | Prompt injection trying to exfiltrate API keys | Content sanitized via `AgentSafetyPolicy`; keys reside strictly in Tink Keystore |
| **Runaway Agent Loop** | Resource exhaustion / API bill explosion | `AgentSafetyPolicy.evaluatePlanInvariants()` caps plan steps at 30 and detects repeat loops |
| **Unapproved Outbound Action**| Sending unauthorized email to recruiter | Blocked: `HumanApprovalGate` strictly requires explicit user confirmation for `EXTERNAL_SIDE_EFFECT` |
| **Accidental Data Wipe** | Destructive deletion of resume history | Blocked: `DESTRUCTIVE` actions require explicit approval + confirmation prompt |
| **Man-in-the-Middle (MitM)** | Interception of resume data in transit | Blocked: OkHttp SHA-256 certificate pinning halts connection on mismatched certificates |
| **Log Leakage** | PII or keys leaked in Logcat / crash reports | Blocked: `CareerOperationTracer` strips all `key`, `token`, `secret`, `email` from metadata |

---

## 4. Security Recommendations for Future Phases

1. **SafetyNet / Play Integrity Attestation**: Expand `PlayIntegrityManager` to enforce hardware attestation before decrypting local Keystore material.
2. **Dynamic Taint Tracking**: Introduce compile-time annotations (`@SensitivePii`) to trace personal data flow across multiplatform modules.
