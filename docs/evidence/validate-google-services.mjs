#!/usr/bin/env node
// Validate a google-services.json for the Aivance "Continue with Google" flow.
//
// It proves, from the file itself, every property that must hold for the
// google-services Gradle plugin to emit a usable default_web_client_id and for
// Firebase/Credential Manager Google sign-in to succeed on the DEBUG build.
//
// Success-only marker (printed after every assertion passes):
//   GOOGLE_SERVICES_OK
//
// Any failure prints GOOGLE_SERVICES_FAIL lines and exits non-zero, so this
// script is a valid negative control: run it against an incomplete file and it
// must fail.
//
// Usage:
//   node validate-google-services.mjs [path-to-google-services.json]
// Defaults to app/google-services.json relative to the repo root (two levels up
// from this script's docs/evidence/ location).

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..', '..');

// The package the built variant will actually run as. Override with
//   --package <pkg>  (portable across cmd.exe and POSIX shells), or
//   the AIVANCE_GS_PACKAGE env var.
// Default target is the debug applicationId used by the emulator build
// (Option A). The Firebase Android OAuth client (client_type 1) carrying the
// debug keystore SHA-1 is registered under this package.
const args = process.argv.slice(2);
let packageFlag;
const positional = [];
for (let i = 0; i < args.length; i++) {
  if (args[i] === '--package' || args[i] === '-p') {
    packageFlag = args[++i];
  } else if (args[i].startsWith('--package=')) {
    packageFlag = args[i].slice('--package='.length);
  } else {
    positional.push(args[i]);
  }
}
const REQUIRED_PACKAGE =
  packageFlag || process.env.AIVANCE_GS_PACKAGE || 'com.bangersoul.aivance.debug';
// Debug keystore SHA-1 (alias androiddebugkey) measured from ~/.android/debug.keystore,
// normalized to lowercase with colons stripped (matches certificate_hash format).
const DEBUG_SHA1_HEX = '7c0839d64775772ec52d76edcb3f5f4264c8b84b';

const target = positional[0]
  ? resolve(positional[0])
  : resolve(repoRoot, 'app', 'google-services.json');

const failures = [];
function fail(msg) {
  failures.push(msg);
}

let json;
try {
  json = JSON.parse(readFileSync(target, 'utf8'));
} catch (err) {
  console.log(`GOOGLE_SERVICES_FAIL unreadable-or-invalid-json: ${target}: ${err.message}`);
  console.log('GOOGLE_SERVICES_FAIL');
  process.exit(1);
}

const projectNumber = json?.project_info?.project_number;
const projectId = json?.project_info?.project_id;

if (!projectNumber || !/^\d{6,}$/.test(String(projectNumber))) {
  fail(`project_info.project_number missing or malformed: ${JSON.stringify(projectNumber)}`);
}
if (!projectId) {
  fail('project_info.project_id missing');
}

const clients = Array.isArray(json?.client) ? json.client : [];
if (clients.length === 0) {
  fail('client[] is empty');
}

// Find the client entry for the debug package under test.
const debugClient = clients.find(
  (c) => c?.client_info?.android_client_info?.package_name === REQUIRED_PACKAGE,
);

if (!debugClient) {
  const seen = clients
    .map((c) => c?.client_info?.android_client_info?.package_name)
    .filter(Boolean);
  fail(
    `no client entry for package ${REQUIRED_PACKAGE} (found: ${seen.length ? seen.join(', ') : 'none'})`,
  );
}

if (debugClient) {
  // app_id middle segment must equal the project number.
  const appId = debugClient?.client_info?.mobilesdk_app_id ?? '';
  const mid = String(appId).split(':')[1];
  if (mid !== String(projectNumber)) {
    fail(`mobilesdk_app_id project segment (${mid}) != project_number (${projectNumber})`);
  }

  const oauth = Array.isArray(debugClient.oauth_client) ? debugClient.oauth_client : [];
  if (oauth.length === 0) {
    fail(`oauth_client[] is empty for ${REQUIRED_PACKAGE} (enable Google sign-in + add SHA-1 in Firebase)`);
  }

  // A Web client (type 3) is what becomes default_web_client_id.
  const webClient = oauth.find((o) => o?.client_type === 3);
  if (!webClient) {
    fail('no Web OAuth client (client_type 3) -> google-services plugin emits no default_web_client_id');
  } else if (!/^\d+-.+\.apps\.googleusercontent\.com$/.test(String(webClient.client_id))) {
    fail(`Web client_id malformed: ${JSON.stringify(webClient.client_id)}`);
  }

  // An Android client (type 1) with the debug SHA-1 lets the ID token be issued.
  const androidClients = oauth.filter((o) => o?.client_type === 1);
  if (androidClients.length === 0) {
    fail('no Android OAuth client (client_type 1) -> debug SHA-1 not registered in Firebase');
  } else {
    const hashes = androidClients
      .map((o) => String(o?.android_info?.certificate_hash ?? '').toLowerCase())
      .filter(Boolean);
    if (!hashes.includes(DEBUG_SHA1_HEX)) {
      fail(
        `debug SHA-1 ${DEBUG_SHA1_HEX} not present among Android client hashes: ${hashes.join(', ') || 'none'}`,
      );
    }
  }

  const apiKeys = Array.isArray(debugClient.api_key) ? debugClient.api_key : [];
  if (!apiKeys.some((k) => k?.current_key)) {
    fail('no api_key.current_key');
  }
}

if (failures.length > 0) {
  for (const f of failures) console.log(`GOOGLE_SERVICES_FAIL ${f}`);
  console.log(`GOOGLE_SERVICES_FAIL total=${failures.length} file=${target}`);
  process.exit(1);
}

console.log(`validated ${target}`);
console.log(`project=${projectId} number=${projectNumber} package=${REQUIRED_PACKAGE}`);
console.log('GOOGLE_SERVICES_OK');
