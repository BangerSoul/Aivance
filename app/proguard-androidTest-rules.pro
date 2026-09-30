# R8 rules for the androidTest APK (`app/build.gradle.kts` -> `testProguardFiles`).
#
# Scope: the *instrumentation harness* APK only. The app under test is minified
# separately by `minifyReleaseWithR8`, and that minification is the coverage this
# setup exists for. Nothing here reduces it.
#
# What actually caused the failures was NOT harness shrinking. It was a link
# mismatch. R8 obfuscates and shrinks classes in the app APK; the harness links
# classes AND members out of that same dependency closure by their original
# names and signatures; the two APKs then disagree and the instrumentation
# process dies before any test runs. Four distinct symbols hit it --
# `androidx.tracing.Trace` (from `androidx.test:runner`'s
# `AndroidJUnitRunner.onCreate`), `kotlin.LazyKt` (from `androidx.test:monitor`'s
# `TestDirCalculator`), `androidx.tracing.Trace.beginSection` (same runner,
# once the class itself resolved but R8 had inlined the method away) and
# `kotlinx.coroutines.DelayWithTimeoutDiagnostics` (shrunk out entirely; only
# the tests reach it). All are fixed on the app side, in `proguard-rules.pro`,
# under "Instrumented-test bridge". These rules are the hardening around that,
# and each earns its place:
#
# -dontobfuscate  A crashed harness is the artifact you actually need to read, and
#                 an obfuscated stack trace is close to worthless. Keeping names
#                 turned `onCreate(r8-map-id-2dcd71...:26)` into
#                 `onCreate(AndroidJUnitRunner.java:307)`, which is how the real
#                 cause was identified in the first place.
# -dontshrink     The harness is uploaded to the device by connectedAndroidTest,
#                 run, and discarded. It is never shipped and never downloaded by
#                 a user, so shrinking it saves nothing while leaving another
#                 way for a transitively-referenced class to go missing.
# -dontoptimize   Same reasoning; also avoids class merging changing type
#                 identities the runner reflects over.
# -dontnote/-dontwarn  This binary is not distributed, so its R8 notes and
#                 warnings are noise in an otherwise readable CI log.
# -keep com.bangersoul.aivance.**  Defence in depth for the test classes
#                 themselves. Note this did NOT rescue ProviderIntegrationTest's
#                 anonymous `object : JobCache` (compiled to the synthetic
#                 ProviderIntegrationTest$jobCache$1), which was still dropped
#                 from the APK and which JUnit surfaced through
#                 MethodSorter.getDeclaredMethods; that test was changed to use
#                 a named object instead. The keep stays because a test class
#                 vanishing is always a whole-class error, not one failed
#                 assertion. Test-only APK, so it costs nothing.

-dontobfuscate
-dontshrink
-dontoptimize
-dontnote
-dontwarn
-keep class com.bangersoul.aivance.** { *; }
