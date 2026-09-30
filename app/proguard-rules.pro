# Aivance ProGuard / R8 Rules
# ============================================================

# Keep data classes used with Kotlinx Serialization
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.bangersoul.aivance.**$$serializer { *; }
-keepclassmembers class com.bangersoul.aivance.** {
    *** Companion;
}
-keepclasseswithmembers class com.bangersoul.aivance.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Room entities and DAOs
-keep class com.bangersoul.aivance.core.database.model.** { *; }
-keep class com.bangersoul.aivance.core.database.dao.** { *; }

# Keep Hilt injected classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep Retrofit interfaces
-keep,allowobfuscation interface com.bangersoul.aivance.**.api.** { *; }
-keep,allowobfuscation interface com.bangersoul.aivance.**.*Api { *; }

# ============================================================
# Instrumented-test bridge: app APK <-> androidx.test harness
# ============================================================
# `app` runs its instrumented suite against this minified release APK. The
# androidTest APK is built separately and is NOT obfuscated (see
# `proguard-androidTest-rules.pro`), yet both APKs are loaded into the same
# classloader. The harness therefore resolves classes out of the app's
# dependency closure by their ORIGINAL names, while R8 has already renamed
# them in place. The two APKs disagree about what a class is called, and the
# instrumentation process dies before a single test executes.
#
# Two real instances, both found by running the suite, not by reading rules:
#
#   androidx.tracing.Trace
#     androidx.test:runner -> AndroidJUnitRunner.onCreate()
#     NoClassDefFoundError: Failed resolution of: Landroidx/tracing/Trace;
#
#   kotlin.LazyKt
#     androidx.test:monitor -> io.TestDirCalculator.<init> (`by lazy {}`)
#     NoClassDefFoundError: Failed resolution of: Lkotlin/LazyKt;
#
# The second one is the sharper lesson: it is a `by lazy {}` the *harness* owns,
# not the app's. No amount of reading this project's source would have surfaced
# it -- only running the release variant on a device did.
#
# Triage rule for anything new that fails this way: add the class to the tier
# below that matches WHY it broke. Don't add a single-class `-keep` per
# incident; the harness links a whole closure of them.
#
# Tier 1 -- must be present AND keep its original name.
# R8 never sees the harness's reference, so it is free to shrink or inline
# these classes even though something genuinely needs them at runtime. Keep
# the names, but still allow body optimization so R8 keeps working.
-keep,allowoptimization class kotlin.** { *; }

# Tier 2 -- must keep its original name, but R8 already keeps these because
# the app uses them; renaming is the only thing that breaks.
# Each entry is reachable from the app APK *and* linkable from
# androidx.test:runner / monitor / core.
-keepnames class androidx.tracing.**
-keepnames class androidx.test.**
-keepnames class androidx.annotation.**
-keepnames class androidx.lifecycle.**
-keepnames class androidx.core.**
-keepnames class kotlinx.coroutines.**
-keepnames class com.google.common.**
-keepnames class com.google.gson.**
-keepnames class org.junit.**
-keepnames class org.hamcrest.**

# OkHttp and Retrofit ship their own consumer rules; only suppress the
# optional-JVM-class warnings (okio/java9). App Retrofit interfaces are kept
# separately above so R8 can shrink the libraries themselves.
-dontwarn okhttp3.**
-dontwarn retrofit2.**

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep Timber
-keep class timber.log.Timber** { *; }

# Compose ships consumer rules — no blanket keep needed. Dropping it lets R8
# shrink the framework (the single largest contributor to APK size).

# Keep Navigation (navigation3 internals are newer than its consumer rules)
-keep class androidx.navigation.** { *; }

# Keep Firebase
-keep class com.google.firebase.** { *; }

# Keep DataStore
-keep class androidx.datastore.** { *; }

# Remove logging in release
-assumenosideeffects class timber.log.Timber {
    public static void v(...);
    public static void d(...);
    public static void i(...);
    public static void w(...);
}
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

# Keep explicit rules from rules.keep
-keep @interface com.bangersoul.aivance.**.Keep { *; }

# Gson/Room type adapters
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============================================================
# R8 missing-class suppressions (generated by AGP)
# Optional JVM-only classes referenced by log4j-api and its
# transitive providers (bnd annotations, java.awt, Saxon,
# OSGi). They are not on the Android runtime classpath.
# ============================================================
-dontwarn aQute.bnd.annotation.spi.ServiceConsumer
-dontwarn aQute.bnd.annotation.spi.ServiceProvider
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn java.awt.Color
-dontwarn java.awt.color.ColorSpace
-dontwarn java.awt.geom.AffineTransform
-dontwarn java.awt.geom.Dimension2D
-dontwarn java.awt.geom.Path2D
-dontwarn java.awt.geom.PathIterator
-dontwarn java.awt.geom.Point2D
-dontwarn java.awt.geom.Rectangle2D
-dontwarn java.awt.image.BufferedImage
-dontwarn java.awt.image.ColorModel
-dontwarn java.awt.image.ComponentColorModel
-dontwarn java.awt.image.DirectColorModel
-dontwarn java.awt.image.IndexColorModel
-dontwarn java.awt.image.PackedColorModel
-dontwarn javax.xml.stream.Location
-dontwarn javax.xml.stream.XMLStreamException
-dontwarn javax.xml.stream.XMLStreamReader
-dontwarn net.sf.saxon.Configuration
-dontwarn net.sf.saxon.dom.DOMNodeWrapper
-dontwarn net.sf.saxon.om.Item
-dontwarn net.sf.saxon.om.NamespaceUri
-dontwarn net.sf.saxon.om.NodeInfo
-dontwarn net.sf.saxon.om.Sequence
-dontwarn net.sf.saxon.om.SequenceTool
-dontwarn net.sf.saxon.sxpath.IndependentContext
-dontwarn net.sf.saxon.sxpath.XPathDynamicContext
-dontwarn net.sf.saxon.sxpath.XPathEvaluator
-dontwarn net.sf.saxon.sxpath.XPathExpression
-dontwarn net.sf.saxon.sxpath.XPathStaticContext
-dontwarn net.sf.saxon.sxpath.XPathVariable
-dontwarn net.sf.saxon.tree.wrapper.VirtualNode
-dontwarn net.sf.saxon.value.DateTimeValue
-dontwarn net.sf.saxon.value.GDateValue
-dontwarn org.osgi.framework.Bundle
-dontwarn org.osgi.framework.BundleContext
-dontwarn org.osgi.framework.FrameworkUtil
-dontwarn org.osgi.framework.ServiceReference
