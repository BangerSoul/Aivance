#!/usr/bin/env python3
"""
Aivance Security Certification — reproducible verification harness.

Independently re-verifies every confirmed remediation with fresh evidence:

  [1] Certificate pins match the LIVE TLS chains (leaf SPKI SHA-256 hex).
  [2] No placeholder/fabricated pins remain in the registry.
  [3] No hardcoded secrets in source (API keys, passwords, tokens).
  [4] Release BuildConfig embeds no real provider keys.
  [5] Backup rules exclude the Room DB and keyset/secret prefs.
  [6] No fallbackToDestructiveMigration in DatabaseModule.
  [7] EncryptionService has no plaintext fallback path.

Run:  python security_scan.py
Exit 0 when every check passes; non-zero otherwise.
"""
import hashlib
import re
import ssl
import socket
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
FAILURES = []


def check(name, ok, detail=""):
    status = "PASS" if ok else "FAIL"
    print(f"[{status}] {name}" + (f" — {detail}" if detail else ""))
    if not ok:
        FAILURES.append(name)


def fetch_live_pins(hosts):
    """Return {host: {pos: hex}} SPKI SHA-256 digests from each live chain.

    Same verified method as extract_pins.py: openssl `-showcerts` -> PEM -> DER
    -> cryptography SPKI SHA-256. This produced the registry values themselves,
    so a re-run is an apples-to-apples re-verification.
    """
    from cryptography.hazmat.primitives import serialization
    from cryptography import x509

    out = {}
    subs = {}
    for h in hosts:
        try:
            r = subprocess.run(
                ["openssl", "s_client", "-connect", f"{h}:443", "-servername", h, "-showcerts"],
                input=b"", capture_output=True, timeout=25,
            ).stdout.decode("utf-8", "replace")
            pems = []
            cur, in_cert = [], False
            for line in r.splitlines():
                if "BEGIN CERTIFICATE" in line:
                    cur, in_cert = [line], True
                elif in_cert:
                    cur.append(line)
                    if "END CERTIFICATE" in line:
                        pems.append("\n".join(cur))
                        in_cert = False
            pins = {}
            subjects = {}
            for i, pem in enumerate(pems):
                der = ssl.PEM_cert_to_DER_cert(pem)
                cert = x509.load_der_x509_certificate(der)
                spki = cert.public_key().public_bytes(
                    serialization.Encoding.DER,
                    serialization.PublicFormat.SubjectPublicKeyInfo,
                )
                pins[i] = hashlib.sha256(spki).hexdigest()
                subjects[i] = (cert.subject.rfc4514_string(),
                               cert.issuer.rfc4514_string())
            out[h] = pins
            subs[h] = subjects
        except Exception as e:  # noqa: BLE001
            out[h] = {"error": str(e)}
            subs[h] = {}
    return out, subs


def parse_pin_registry():
    """Extract hex pins and hosts from CertificatePins.kt."""
    src = (ROOT / "core/common/src/main/java/com/bangersoul/aivance/core/common/security/CertificatePins.kt").read_text(encoding="utf-8")
    hex_pins = {}
    for m in re.finditer(r'"([a-f0-9]{64})"', src):
        pass  # dedupe below by host mapping
    # hosts are the keys of HEX_PINS map; approximate host->pin via proximity
    host_blocks = re.findall(r'"([a-z0-9.-]+)"\s*to\s*listOf\((.*?)\)\s*,?\s*$', src, re.M | re.S)
    for host, body in host_blocks:
        pins = re.findall(r'"([a-f0-9]{64})"', body)
        if pins:
            hex_pins[host] = pins
    return hex_pins


def main():
    print("=" * 70)
    print("AIVANCE SECURITY CERTIFICATION — VERIFICATION HARNESS")
    print("=" * 70)

    # ── [1] Live pin verification ─────────────────────────────────────────
    registry = parse_pin_registry()
    check("Pin registry parsed (CertificatePins.kt)", bool(registry),
          f"{len(registry)} hosts: {', '.join(sorted(registry))}")
    if registry:
        live, live_subjects = fetch_live_pins(sorted(registry))
        for host, pins in registry.items():
            live_pins = live.get(host, {})
            if "error" in live_pins:
                check(f"Live fetch {host}", False, live_pins["error"])
                continue
            leaf = live_pins.get(0)
            # any registered pin (leaf + CA) must appear somewhere in the live chain
            chain_all = set(live_pins.values())
            overlap = set(pins) & chain_all
            detail = (f"{len(overlap)}/{len(pins)} registered pins present in chain"
                      + (f" (leaf={leaf[:16]}...)" if leaf else ""))
            if not overlap:
                # A fleet-served host (Google Frontend) can drop the runner on an
                # edge whose CA path none of our pins cover, and that is not
                # always a pinning bug. Print the whole presented chain: the fix
                # is to add the CA that edge issues under, and there is no way
                # to know which one that is without seeing it.
                for pos, (subj, issuer) in sorted(live_subjects.get(host, {}).items()):
                    detail += (f"\n        [{pos}] subject={subj}"
                               f"\n            issuer={issuer}"
                               f"\n            pin={live_pins.get(pos, '')}")
            check(f"Pins match live chain {host}", bool(overlap), detail)

    # ── [2] No placeholders / fabricated pins ─────────────────────────────
    src = (ROOT / "core/common/src/main/java/com/bangersoul/aivance/core/common/security/CertificatePins.kt").read_text(encoding="utf-8")
    bad_pin_patterns = [
        "ADD_YOUR_", "REPLACE_WITH_", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "c7e3f89025e1a38f7f4d2a10b9e8d7c6b5a4f3e2d1c0b9a8f7e6d5c4b3a2f1e0",  # old fabricated
        "9f8e7d6c5b4a3f2e1d0c9b8a7f6e5d4c3b2a1f0e9d8c7b6a5f4e3d2c1b0a9f8e",  # old fabricated
    ]
    check("No placeholder/fabricated pins", not any(p in src for p in bad_pin_patterns))

    # ── [3] Hardcoded secrets scan ────────────────────────────────────────
    secret_patterns = [
        (r"api[Kk]ey\s*=\s*\"(?!\")", "apiKey = \"<value>\""),
        (r"secret\s*=\s*\"[A-Za-z0-9+/]{16,}", "secret = \"<value>\""),
        (r"password\s*=\s*\"(?!\")", "password = \"<value>\""),
        (r"token\s*=\s*\"[A-Za-z0-9._-]{20,}", "token = \"<long-value>\""),
        (r"DEFAULT_PASSPHRASE\s*=\s*\"[^\"]+\"", "hardcoded backup passphrase"),
        (r"\bsk-[A-Za-z0-9]{20,}", "OpenAI sk- key"),
        (r"AIza[A-Za-z0-9_-]{30,}", "Google API key"),
    ]
    hits = []
    for f in (ROOT / "core").rglob("*.kt"):
        parts = [p.lower() for p in f.parts]
        if "build" in parts or "test" in parts or "androidtest" in parts:
            continue
        text = f.read_text(encoding="utf-8", errors="replace")
        for pat, label in secret_patterns:
            for m in re.finditer(pat, text):
                hits.append(f"{f.relative_to(ROOT)}:{label}")
    check("No hardcoded secrets in core sources", not hits, "; ".join(hits[:5]))

    # ── [4] Release BuildConfig hygiene ───────────────────────────────────
    # A provider credential must never reach the release BuildConfig. The
    # androidTest source set compiles against the *release* variant (see
    # `testBuildType` in app/build.gradle.kts), so the fields ProviderIntegrationTest
    # reads have to be declared there — but every one of them must be a hardcoded
    # empty literal.
    #
    # This checks assigned *values*, not the presence of the field name. The old
    # check only asked whether the name appeared in the release block, which both
    # rejected the legitimate empty declaration and was blind to the case that
    # actually matters: a leak that never spells the name, e.g.
    # `integrationApiKey("groqApiKey")`.
    EMPTY_LITERAL = '"\\"\\""'

    def key_field_values(block, key):
        """Assigned values of every `buildConfigField` declared for `key`."""
        return re.findall(
            r'buildConfigField\(\s*"String"\s*,\s*"' + re.escape(key) + r'"\s*,\s*(.+?)\s*\)',
            block,
        )

    non_empty_release_values = [
        f"{k} = {v}"
        for k in ("APIFY_API_KEY", "GROQ_API_KEY", "GEMINI_API_KEY", "HUNTER_API_KEY")
        for v in key_field_values(release_block, k)
        if v != EMPTY_LITERAL
    ]
    # `integrationApiKey` is the only path by which local.properties (which holds
    # real provider keys) can reach a buildConfigField. Forbidding the call in the
    # release block closes leaks that never mention a field name.
    release_reads_local_props = "integrationApiKey" in release_block

    app_bs = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    default_cfg = app_bs.split("buildTypes")[0]
    release_block = app_bs.split("release {")[1].split("debug {")[0] if "release {" in app_bs else ""
    keys_in_default = [k for k in ("APIFY_API_KEY", "GROQ_API_KEY", "GEMINI_API_KEY", "HUNTER_API_KEY")
                       if f'"{k}"' in default_cfg]
    net_bs = (ROOT / "core/network/build.gradle.kts").read_text(encoding="utf-8")
    net_release = net_bs.split("release {")[1].split("}")[0] if "release {" in net_bs else ""
    net_key_in_release = "GEMINI_API_KEY" in net_release and '""' not in net_release
    check("No API keys in defaultConfig", not keys_in_default, f"found: {keys_in_default}")
    check("Release buildType declares no non-empty provider key", not non_empty_release_values,
          "; ".join(non_empty_release_values))
    check("Release buildType never reads local.properties credentials", not release_reads_local_props)
    check("core:network release has empty GEMINI_API_KEY", not net_key_in_release)

    # ── [5] Backup rules exclude DB + keysets ─────────────────────────────
    for name in ("backup_rules.xml", "data_extraction_rules.xml"):
        xml = (ROOT / f"app/src/main/res/xml/{name}").read_text(encoding="utf-8")
        excl_db = '<exclude domain="database" path="."' in xml
        excl_keyset = ("aivance_tink_prefs" in xml and "aivance_security_prefs" in xml)
        check(f"{name} excludes DB", excl_db)
        check(f"{name} excludes keyset prefs", excl_keyset)

    # ── [6] No destructive fallback ───────────────────────────────────────
    db_mod = (ROOT / "core/database/src/main/java/com/bangersoul/aivance/core/database/di/DatabaseModule.kt").read_text(encoding="utf-8")
    check("No fallbackToDestructiveMigration", "fallbackToDestructiveMigration" not in db_mod)

    # ── [7] EncryptionService fail-closed ─────────────────────────────────
    enc = (ROOT / "core/database/src/main/java/com/bangersoul/aivance/core/database/security/EncryptionService.kt").read_text(encoding="utf-8")
    check("EncryptionService fail-closed (no plaintext return)",
          "aead ?: return plainText" not in enc and "return plainText" not in enc
          and "return cipherText" not in enc)

    # ── Result ────────────────────────────────────────────────────────────
    print("=" * 70)
    if FAILURES:
        print(f"RESULT: {len(FAILURES)} CHECK(S) FAILED")
        for f in FAILURES:
            print(f"  - {f}")
        sys.exit(1)
    print("RESULT: ALL SECURITY CHECKS PASS")


if __name__ == "__main__":
    main()
