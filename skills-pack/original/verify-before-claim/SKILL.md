---
name: verify-before-claim
description: Verify every external fact before claiming it — URLs, model file sizes, certificate pins, free-tier limits, API response shapes, dependency versions. Use whenever adding or documenting an external integration (API URL, model download, cert pin, auth scheme, dependency), whenever a doc/KDoc/string claims a concrete number or that something "exists" or "works", and whenever a security or data claim is being certified. Never assert a live fact from memory; prove it with a command and record the evidence.
---

# Verify Before Claim

This project's #1 rule: **no fabricated facts**. A claim that a URL exists, a
model is ~1.3 GB, a pin is real, or an API returns JSON is a *testable
hypothesis*, not a docstring. Untested claims rot silently and — worse — get
shipped (this repo once shipped 16 fabricated TLS pins that "looked real").

## When to trigger

- Adding or changing a **URL** (API endpoint, model download, webhook).
- Documenting a **size, limit, quota, or latency** ("~2.9 GB", "free tier", "60 req/min").
- Writing or changing a **certificate pin** / SPKI hash.
- Claiming a **dependency version** exists in a repo (Maven Central, Google Maven, npm).
- Claiming an **API shape** (field names, response codes) without reading docs.
- Marking a security/DB/certification claim **RESOLVED** in KNOWN_ISSUES.md.
- Writing a **provider** that hits a real service (see `provider-sdk-extension`).

## The rule

> Every concrete external claim must be accompanied by the command that proved
> it and the evidence it returned — in the KDoc, the commit message, or the
> issue entry. If you can't prove it, don't claim it; say "unverified".

## Verification playbook

### 1. Does a URL resolve / is a file the right size?

```bash
# Follow redirects, show final size (models/CDNs redirect via 302):
curl -sIL "<url>" 2>/dev/null | grep -iE 'HTTP/|content-length' | tail -3
# Exact size of a resumable file (CDNs may omit content-length on HEAD):
curl -sL -r 0-0 -D - -o /dev/null "<url>" 2>/dev/null | grep -i content-range
```

Record the exact byte count. Convert honestly: `bytes / 1024^3` (GiB) vs
`bytes / 1000^3` (GB) differ by 7% — say which one you used.

### 2. Does the dependency exist in this repo/version?

```bash
# Google Maven (MediaPipe, AndroidX):
curl -s "https://dl.google.com/dl/android/maven2/com/google/mediapipe/tasks-genai/maven-metadata.xml" | grep -o '<version>[^<]*</version>' | tail -5
# Maven Central:
curl -s "https://repo1.maven.org/maven2/<group>/<artifact>/maven-metadata.xml" | grep -o '<version>[^<]*</version>' | tail -5
```

### 3. Is the API response shape what I think it is?

- Read the official docs page (never a mirror) before writing the parser.
- If you must assert a field name, `curl` a live sample endpoint and paste the
  real JSON into the test fixture.

### 4. Is the certificate pin real?

```bash
echo | openssl s_client -connect <host>:443 -servername <host> 2>/dev/null \
  | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der \
  | openssl dgst -sha256 -binary | openssl enc -base64
```

Compare against the pin registry. Never invent a hash — the security scanner
(`security_scan.py`) catches placeholders.

### 5. Is a "free tier" claim current?

Free tiers change. Check the provider's pricing page *and* a real signup/console
screenshot-level source, then date-stamp the claim ("verified 2026-08-08").

## Recording the evidence

In KDoc or a constant, write:

```kotlin
/**
 * Default source for the model. Size verified via HTTP Range request on
 * 2026-08-08: content-range bytes 0-0/3136226711 (≈2.9 GiB).
 */
const val DEFAULT_MODEL_URL: String = "https://..."
```

In KNOWN_ISSUES.md resolutions, end with:
`Evidence: <test counts> green + <command> returned <result> (date).`

## Anti-patterns

- ❌ "~1.3 GB model" written from memory (was actually 3.1 GB — a real bug).
- ❌ Pins that "look like" base64 but are countdown sequences.
- ❌ "API returns `data.items`" without ever calling it.
- ❌ Marking a certification RESOLVED without executable evidence.
- ✅ Saying "unverified — needs a device pass" instead of guessing.
