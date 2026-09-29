# Aivance Release Checklist

> How-to (keystore setup, signing env vars, build/verify commands) lives in
> [RELEASE.md](RELEASE.md). This is the pre-flight *what-to-verify* list.

## Pre-Release Checklist

### Build Verification
- [ ] `./gradlew assembleRelease` succeeds
- [ ] `./gradlew testRelease` passes all tests
- [ ] `./gradlew lintRelease` has no errors
- [ ] APK/AAB size within budget
- [ ] R8/ProGuard optimization verified
- [ ] Baseline profile generated and bundled

### Functional Testing
- [ ] All 15 screens render correctly
- [ ] Authentication flow works (login, register, forgot password)
- [ ] Resume import, edit, export flow works
- [ ] AI chat sends and receives messages
- [ ] Job search returns results from all providers
- [ ] Interview session flow works end-to-end
- [ ] Cover letter generation works
- [ ] Career roadmap generates and displays
- [ ] Navigation and deep links work
- [ ] Offline mode works (airplane mode test)
- [ ] Background sync works (resume analysis, job sync)

### Security
- [ ] Certificate pinning configured with real SHA-256 hashes
- [ ] Network security config verified
- [ ] Encrypted storage (DataStore + AndroidKeyStore) verified
- [ ] Input validation on all text fields
- [ ] Deep link validation active (aivance:// scheme)
- [ ] Play Integrity check passes
- [ ] Root detection active
- [ ] ProGuard/R8 obfuscation verified

### Performance
- [ ] Cold startup < 2s on reference device (Pixel 6)
- [ ] Warm startup < 500ms
- [ ] No jank in scrolling lists
- [ ] Memory usage < 150MB baseline
- [ ] ANR rate < 0.1%
- [ ] Database queries < 50ms
- [ ] Network timeouts handled gracefully

### Accessibility
- [ ] TalkBack navigation works for all screens
- [ ] Content descriptions on all icons and buttons
- [ ] Touch targets >= 48dp
- [ ] Color contrast meets WCAG AA
- [ ] Font scaling works up to 200%

### Privacy & Compliance
- [ ] Privacy policy accepted before tracking
- [ ] Consent management implemented
- [ ] GDPR data portability endpoint ready
- [ ] GDPR deletion endpoint ready
- [ ] Data retention policy documented
- [ ] Third-party SDK list audited
- [ ] License compliance verified

### Production Configuration
- [ ] Debug logging disabled
- [ ] BuildConfig.DEBUG paths removed
- [ ] Staging URLs replaced with production URLs
- [ ] Mock data removed
- [ ] Developer-only features disabled
- [ ] Crash reporting enabled
- [ ] Analytics enabled
- [ ] API keys from secure source (environment variables)

### Release Artifacts
- [ ] AAB signed with release key
- [ ] App Bundle generated
- [ ] ProGuard mapping file saved
- [ ] Native debug symbols saved
- [ ] Version code and name updated
- [ ] Release notes written
- [ ] Changelog updated

## Post-Release
- [ ] Rollout started (staged: 10% → 25% → 50% → 100%)
- [ ] Crash-free rate monitored (target > 99.5%)
- [ ] ANR rate monitored (target < 0.1%)
- [ ] Performance metrics verified
- [ ] User feedback collected
- [ ] Hotfix branch created if needed
