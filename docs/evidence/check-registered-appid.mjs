#!/usr/bin/env node
// Prove the debug build variant will use the registered release applicationId
// (com.bangersoul.aivance) when built with -Paivance.useRegisteredAppId=true.
//
// This reads the merged/processed manifest from the debug build output. It is a
// real observation of what the build produced, not a re-parse of the gradle DSL.
//
// Success-only marker: REGISTERED_APPID_OK
// Failure marker:      REGISTERED_APPID_FAIL
//
// The AndroidManifest package= is stripped by AGP; the applicationId lands in
// output-metadata.json and in the APK. We read output-metadata.json for the
// debug variant, which records the exact applicationId AGP assigned.

import { readFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..', '..');

const REQUIRED_APPID = 'com.bangersoul.aivance';

const metadata = resolve(
  repoRoot,
  'app',
  'build',
  'outputs',
  'apk',
  'debug',
  'output-metadata.json',
);

if (!existsSync(metadata)) {
  console.log(`REGISTERED_APPID_FAIL output-metadata.json not found: ${metadata}`);
  console.log('REGISTERED_APPID_FAIL run: ./gradlew.bat :app:assembleDebug -Paivance.useRegisteredAppId=true');
  process.exit(1);
}

let meta;
try {
  meta = JSON.parse(readFileSync(metadata, 'utf8'));
} catch (err) {
  console.log(`REGISTERED_APPID_FAIL invalid output-metadata.json: ${err.message}`);
  process.exit(1);
}

const appId = meta?.applicationId;
if (appId !== REQUIRED_APPID) {
  console.log(`REGISTERED_APPID_FAIL applicationId is "${appId}", expected "${REQUIRED_APPID}"`);
  console.log('REGISTERED_APPID_FAIL rebuild with -Paivance.useRegisteredAppId=true');
  process.exit(1);
}

console.log(`applicationId=${appId}`);
console.log('REGISTERED_APPID_OK');
