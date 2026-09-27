#!/usr/bin/env node
// Negative control for the google-services validator.
//
// Runs validate-google-services.mjs against a config expected to be INVALID
// and inverts the result: prints the success token ONLY when validation fails.
// This proves the validator can actually reject a bad file (guards against a
// validator that always passes).
//
// Portable: pure Node, no shell operators, so it behaves identically under
// cmd.exe, PowerShell, and bash.
//
// Success-only marker: NEGATIVE_CONTROL_FAILED_AS_EXPECTED
//
// Usage:
//   node negative-control.mjs <path-to-invalid-google-services.json>

import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const __dirname = dirname(fileURLToPath(import.meta.url));
const validator = resolve(__dirname, 'validate-google-services.mjs');

const target = process.argv[2];
if (!target) {
  console.log('NEGATIVE_CONTROL_ERROR missing target path argument');
  process.exit(2);
}

const res = spawnSync(process.execPath, [validator, target], { encoding: 'utf8' });
const combined = `${res.stdout ?? ''}${res.stderr ?? ''}`;

// The known-invalid fixture must make the validator exit non-zero AND emit its
// failure marker. If the validator "passed" a config we expect to be invalid,
// the negative control itself fails.
if (res.status === 0 || combined.includes('GOOGLE_SERVICES_OK')) {
  console.log('NEGATIVE_CONTROL_ERROR validator accepted a config expected to be invalid');
  console.log(combined.trim());
  process.exit(1);
}
if (!combined.includes('GOOGLE_SERVICES_FAIL')) {
  console.log('NEGATIVE_CONTROL_ERROR validator failed without its failure marker');
  console.log(combined.trim());
  process.exit(1);
}

console.log(`validator rejected ${target} (exit=${res.status})`);
console.log('NEGATIVE_CONTROL_FAILED_AS_EXPECTED');
