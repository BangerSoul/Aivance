#!/usr/bin/env node
// Prove that the google-services Gradle plugin produced a real
// default_web_client_id (not a placeholder) from the installed
// app/google-services.json.
//
// The plugin writes it to:
//   app/build/generated/res/processDebugGoogleServices/values/values.xml
// as <string name="default_web_client_id">...</string>
//
// Success-only marker: WEB_CLIENT_ID_OK
// Failure marker:      WEB_CLIENT_ID_FAIL
//
// This intentionally does NOT run Gradle itself; it reads the generated
// artifact so the gate reflects a real build. If the artifact is absent,
// the gate fails and prompts a build.

import { readFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..', '..');

const generated = resolve(
  repoRoot,
  'app',
  'build',
  'generated',
  'res',
  'processDebugGoogleServices',
  'values',
  'values.xml',
);

if (!existsSync(generated)) {
  console.log(`WEB_CLIENT_ID_FAIL generated resource not found: ${generated}`);
  console.log('WEB_CLIENT_ID_FAIL run: ./gradlew.bat :app:processDebugGoogleServices');
  process.exit(1);
}

const xml = readFileSync(generated, 'utf8');
const m = xml.match(/<string name="default_web_client_id"[^>]*>([^<]+)<\/string>/);
if (!m) {
  console.log('WEB_CLIENT_ID_FAIL default_web_client_id not present in generated values.xml');
  process.exit(1);
}

const id = m[1].trim();
// Must be a real Google web client id and must NOT be the known-fabricated one.
const FABRICATED = '433186935073-elau2khhj78koof6puo1mrk2hifa26jt.apps.googleusercontent.com';
if (id === FABRICATED) {
  console.log(`WEB_CLIENT_ID_FAIL still the fabricated placeholder id: ${id}`);
  process.exit(1);
}
if (!/^\d+-.+\.apps\.googleusercontent\.com$/.test(id)) {
  console.log(`WEB_CLIENT_ID_FAIL malformed default_web_client_id: ${id}`);
  process.exit(1);
}

console.log(`default_web_client_id=${id}`);
console.log('WEB_CLIENT_ID_OK');
