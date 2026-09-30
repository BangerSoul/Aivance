# R8 rules for the androidTest APK (`app/build.gradle.kts` -> `testProguardFiles`).
#
# This file applies to the *instrumentation harness* APK only. The app under test
# is minified separately by `minifyReleaseWithR8`, and that is the coverage this
# setup exists for — nothing here reduces it.
#
# The harness APK is never shipped and never downloaded by a user: it is uploaded
# to the device by `connectedAndroidTest`, runs the suite, and is discarded. So
# shrinking, obfuscating or optimizing it buys nothing at all, while carrying the
# full risk of a bad shrink.
#
# It already did. With `testBuildType = "release"`, AGP runs
# `minifyReleaseAndroidTestWithR8`, and R8 stripped a class the runner needs
# before a single test could execute:
#
#   java.lang.NoClassDefFoundError: Failed resolution of: Landroidx/tracing/Trace;
#       at androidx.test.runner.AndroidJUnitRunner.onCreate(...)
#   Caused by: java.lang.ClassNotFoundException: androidx.tracing.Trace
#
# `androidx.tracing:tracing` arrives transitively via `androidx.test:runner`.
# R8 has no entry-point declaration for the instrumentation runner in the harness
# APK, so a reference from `AndroidJUnitRunner.onCreate` looks unreachable and the
# class is removed. The suite then fails with "Process crashed" and zero tests run,
# which is indistinguishable from a real R8 regression in the app — the exact
# confusion this job exists to remove.
#
# Fix: keep the harness intact. `-dontshrink` alone is the load-bearing directive;
# the rest simply stops R8 emitting notes and warnings for a binary that is not
# distributed. `-dontobfuscate` is included for the same reason — obfuscated test
# class names make a stack trace from a crashed harness unreadable, and a readable
# trace is the entire value of this artifact when something goes wrong.

-dontshrink
-dontoptimize
-dontobfuscate
-dontnote
-dontwarn
