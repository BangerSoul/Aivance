# Gates: Google sign-in on the debug build (Option A: com.bangersoul.aivance.debug)

OWNS: app/google-services.json, docs/evidence/validate-google-services.mjs, docs/evidence/check-web-client-id.mjs, docs/evidence/negative-control.mjs, docs/evidence/fixtures/google-services.incomplete.json

Scope: Build the debug variant with its normal applicationId
com.bangersoul.aivance.debug (the package the Firebase Android OAuth client is
registered under, with the debug keystore SHA-1
7c0839d64775772ec52d76edcb3f5f4264c8b84b), install the assembled
google-services.json, and verify it yields a real default_web_client_id plus an
Android OAuth client bound to the debug SHA-1 for that package.

Note: Option B (build under the registered release appId com.bangersoul.aivance
via -Paivance.useRegisteredAppId=true) was abandoned once the Firebase console
showed the SHA-1 attached to the .debug Android client. The build flag remains
in app/build.gradle.kts but is unused for this flow.

- [x] G0: this ledger states outcomes that can fail
  CHECK: node "C:/Users/BangerSoul/.claude/skills/unlazy/scripts/gate-lint.mjs" .unlazy/google-services/GATES.md
  EXPECT: LINT OK
  EVIDENCE: automatic-evidence=v1; definition-sha256=951f6b07167377452646be1703580477c0004da26e034ece28ad0701be0f2300; exit=0; EXPECT=matched; output-sha256=48630b7361dd44ee870917b12c3d19b9d7bdea738aaca16bb04d4cab83b772d2; output-bytes=8; shell=C:\WINDOWS\system32\cmd.exe; cwd=C:\Users\BangerSoul\Desktop\Projects\Aivance; path=7a68bb920b13/27 entries

- [x] G1: the validator rejects a known-incomplete config (negative control)
  CHECK: node docs/evidence/negative-control.mjs docs/evidence/fixtures/google-services.incomplete.json
  EXPECT: NEGATIVE_CONTROL_FAILED_AS_EXPECTED
  CWD: .
  EVIDENCE: automatic-evidence=v1; definition-sha256=b9cd8aa0f4552bebba67d2d6d779f20ae0dd119bc0edc8ccb026ad3c01ab4806; exit=0; EXPECT=matched; output-sha256=1576cc67ec96dc5cf7fc95ba72b999be860f9c81b17c4054cec27fa6ee36c190; output-bytes=119; shell=C:\WINDOWS\system32\cmd.exe; cwd=C:\Users\BangerSoul\Desktop\Projects\Aivance; path=7a68bb920b13/27 entries

- [x] G2: app/google-services.json is a valid Firebase config with a Web OAuth client and an Android OAuth client bound to the debug SHA-1 for com.bangersoul.aivance.debug
  CHECK: node docs/evidence/validate-google-services.mjs --package com.bangersoul.aivance.debug app/google-services.json
  EXPECT: GOOGLE_SERVICES_OK
  CWD: .
  EVIDENCE: automatic-evidence=v1; definition-sha256=8d7024d9bc6a0eb05178f6c772847dc01cbb9f9e67f788840c42b3cc0d8085a0; exit=0; EXPECT=matched; output-sha256=5fe07b94145302354fae48a4fe07b7de6eb632b0a983729d5a9782b7ef8389c9; output-bytes=178; shell=C:\WINDOWS\system32\cmd.exe; cwd=C:\Users\BangerSoul\Desktop\Projects\Aivance; path=7a68bb920b13/27 entries

- [x] G3: the google-services plugin generates a non-placeholder default_web_client_id from the installed config
  CHECK: node docs/evidence/check-web-client-id.mjs
  EXPECT: WEB_CLIENT_ID_OK
  CWD: .
  EVIDENCE: automatic-evidence=v1; definition-sha256=d65cd656b265b907b218eac022d3ee1c1e3f45100c99b6e5b876445244fcd5c6; exit=0; EXPECT=matched; output-sha256=640492e15fafd2418b9f0005ee4cd4099f945ef138422ceb0c666b2cd190655d; output-bytes=112; shell=C:\WINDOWS\system32\cmd.exe; cwd=C:\Users\BangerSoul\Desktop\Projects\Aivance; path=7a68bb920b13/27 entries
