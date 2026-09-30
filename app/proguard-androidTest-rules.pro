# R8 rules for the androidTest APK (`app/build.gradle.kts` -> `testProguardFiles`).
#
# Scope: the *instrumentation harness* APK only. The app under test is minified
# separately by `minifyReleaseWithR8`, and that minification is the coverage this
# setup exists for. Nothing here reduces it.
#
# What actually caused the first failure was NOT harness shrinking. It was a name
# mismatch: R8 obfuscated `androidx.tracing.Trace` in the app APK, while the
# harness resolved it by its original name from
# `androidx.test:runner`'s `AndroidJUnitRunner.onCreate`, giving
# NoClassDefFoundError before any test ran. That is fixed on the app side, in
# `proguard-rules.pro`, with `-keepnames class androidx.tracing.**`. These rules
# are the hardening around it, and each earns its place:
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

-dontshrink
-dontoptimize
-dontobfuscate
-dontnote
-dontwarn
