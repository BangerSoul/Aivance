# Security Policy

## Reporting a vulnerability

Please report security issues privately rather than opening a public issue.
Email the maintainer address in the README, or use GitHub's
**Security → Report a vulnerability** tab on this repository.

Please include: the affected module, the reproduction steps, and the impact you
observed. We aim to acknowledge within 3 working days.

---

## Certificate pinning

Every provider host the app talks to is pinned to the SHA-256 of its
SubjectPublicKeyInfo (SPKI), in the two forms OkHttp understands:

- `CertificatePins.OKHTTP_PINS` — `sha256/<base64>`, for OkHttp's native
  `CertificatePinner`.
- `CertificatePins.HEX_PINS` — lowercase 64-char hex, for our own
  `CertificatePinningInterceptor`.

**These two maps must stay in sync.** A host matches when **any** of its pins is
present in the presented chain, so each host carries its leaf pin plus the
issuing intermediate and root CA pins. That way an ordinary leaf rotation still
matches through the stable CA pin, and only a CA rotation requires a release.

### Google-fronted hosts need CA pins, not leaf pins

`generativelanguage.googleapis.com` is served by Google Frontend, which draws
from a **rotating pool of leaf certificates** across its edge fleet. Which leaf
you receive depends on routing, not on the host name. Three distinct leaves were
observed across two CI runs and a manual harvest of every reachable IPv4 edge;
one of them is a shared certificate whose subject is `upload.video.google.com`.

Consequences, and the reason this is written down:

- A leaf pin for this host is **best-effort only**. It will match some requests
  and miss others.
- The **WE2 and GTS Root R4 CA pins are the layer that actually matters** here.
  They are what has survived every rotation observed so far, and they are the
  reason a leaf rotation is not an outage.
- `security_scan.py` connects once, from one runner, and reports whichever edge
  it landed on. A `0/3` result for this host usually means that particular edge
  served a chain without WE2 or GTS R4 — **not** that the pins were tampered
  with. Re-run before concluding anything, and harvest across several edges if
  you intend to change the data.

---

## Pin rotation runbook

`security_scan.py` runs in CI (`🔒 1. Security & Certificate Pinning Scan`) and
on a weekly schedule. A failure is pin drift until proven otherwise.

1. **Detect.** The CI job fails with `Pins match live chain <host> — 0/N`.
2. **Re-harvest.** Get the live chain and its SPKI SHA-256 digests:

   ```bash
   openssl s_client -connect <host>:443 -servername <host> -showcerts </dev/null
   ```

   Feed the PEM chain through `x509.load_der_x509_certificate` →
   `public_key().public_bytes(Encoding.DER, SubjectPublicKeyInfo)` →
   `base64.b64encode(sha256(spki))` for the `sha256/` form, and
   `hexlify(sha256(spki))` for the hex form. That is exactly what
   `fetch_live_pins()` in `security_scan.py` does.

   For a Google-fronted host, repeat the harvest against several resolved edge
   IPs so you see the whole pool, not one edge.
3. **Update both maps** for the affected hosts only, and keep the two encodings
   of the same certificate in the same slot.
4. **Verify locally:** `python3 security_scan.py` must print
   `RESULT: ALL SECURITY CHECKS PASS` and exit 0.
5. **Ship** the registry update as a v1.0.x hotfix. Do not "fix" a failure by
   removing a pin — removing one silently degrades to plain host verification.
6. **Record** the rotation in `CHANGELOG.md` and in the verification block at the
   top of `CertificatePins.kt`.

> Never remove a pin to make a connection succeed. Investigate the chain first.

---

## Secret handling

- **No AI-provider or job-API key is ever committed.** Provider keys are entered
  at runtime in Settings → Providers and stored through `SecretsManager` in
  `EncryptedSharedPreferences` (Tink, AES-256-GCM). Gradle build config reads
  `local.properties`, which is not tracked.
- **Instrumented tests self-skip.** `ProviderIntegrationTest` guards every live
  call behind `assumeTrue(...)`, so CI passes without keys rather than failing.
- **`app/google-services.json` is tracked.** It holds the Firebase project id,
  an API key, and OAuth client ids. The API key is *not* a secret in Google's
  model — shipping it is expected — but it is only safe while it is
  **restricted by package name and signing-certificate SHA-1** in the Cloud
  Console. An unrestricted key in a public repository is treated as
  compromised: rotate it, then re-apply the restrictions before shipping.
- Certifying that file before a release:
  `node docs/evidence/validate-google-services.mjs`.
