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

### Google-fronted hosts serve more than one CA path

`generativelanguage.googleapis.com` is served by Google Frontend, which draws
from a rotating pool of leaf certificates **and from more than one issuing CA**.
Which combination you get depends on routing, not on the hostname. Two paths
were observed directly:

| Path | Leaf | Intermediate | Root |
|---|---|---|---|
| A | `upload.video.google.com` | WE2 | GTS Root R4 |
| B | `upload.video.google.com` | **WR2** | **GTS Root R1** |

Consequences, and the reason this is written down:

- Pinning only one path is an **outage for everyone routed to the other**. That
  is not a theoretical risk: the registry carried only path A, the CI runner
  landed on path B, and the check failed at `0/3` — no registered pin matched
  anything in the chain. The Gemini call would have failed for those users too.
- **Both** CAs are pinned for this host, so either path validates. Do not prune
  back to one "because that is the one you keep seeing."
- Leaf pins remain **best-effort** — the pool rotates constantly, and both
  observed leaves carry the same subject. The CA pins are what hold the line.
- The trade-off is real and worth stating plainly: pinning the roots means any
  certificate those CAs issue satisfies the check for this host, so trust is
  placed in the issuing CA rather than in this one certificate. For a
  fleet-served host that is the cheaper side of the trade — the alternative is
  an AI feature that fails by region.
- `security_scan.py` connects once, from one runner, and validates whichever
  edge it landed on. A pass therefore only proves the *sampled* path works, and
  a single green run is not evidence the whole fleet is covered.

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

   When a host fails, the check prints the full presented chain — subject,
   issuer and complete pin for every certificate — so read that output before
   harvesting by hand. It is the chain the failing vantage point actually saw,
   which is precisely the one you cannot reproduce locally.

   For a Google-fronted host, repeat the harvest against several resolved edge
   IPs **and** from more than one network, so you see every CA path rather than
   whichever one you happen to sit behind.
3. **Update both maps** for the affected hosts only, and keep the two encodings
   of the same certificate in the same slot.

   ⚠️ `parse_pin_registry()` in `security_scan.py` finds each host's list with
   a regex that ends the list at the first `)` at end-of-line. A trailing
   comment like `// leaf (WE2 path)` therefore **silently truncates the pin
   list**, and the check then "passes" against a fraction of the real pins.
   Keep end-of-line comments free of closing parentheses, and confirm with
   `python3 -c "import security_scan; print(len(security_scan.parse_pin_registry()['generativelanguage.googleapis.com']))"`
   that the expected number of pins is being read.
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
