import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.google.services)
}

// Phase 4 (STEP 3): real-API integration-test keys. Read from the gitignored
// local.properties and exposed via BuildConfig for androidTest only — never
// commit real keys to source.
val localApiProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun integrationApiKey(name: String): String = localApiProperties.getProperty(name, "").trim()

// ── Release signing ───────────────────────────────────────────────────────────
// Credentials are resolved in this order, most specific first:
//   1. Environment variables  — AIVANCE_STORE_FILE / _STORE_PASSWORD / _KEY_ALIAS /
//      _KEY_PASSWORD. This is what CI uses, so the keystore material lives in
//      repository/environment secrets and never touches the working tree.
//   2. keystore.properties    — the standard Android convention, for local
//      release builds. Gitignored (see .gitignore).
// A keystore alone is never enough: a partially-populated config is reported
// rather than silently half-applied, because a half-configured release build
// produces a signed-looking artifact that nothing can update.
data class SigningCredentials(
    val storeFilePath: String,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

fun readSigningCredentials(): SigningCredentials? {
    val fromEnv = SigningCredentials(
        storeFilePath = System.getenv("AIVANCE_STORE_FILE").orEmpty(),
        storePassword = System.getenv("AIVANCE_STORE_PASSWORD").orEmpty(),
        keyAlias = System.getenv("AIVANCE_KEY_ALIAS").orEmpty(),
        keyPassword = System.getenv("AIVANCE_KEY_PASSWORD").orEmpty(),
    )
    if (fromEnv.let { it.storeFilePath.isNotEmpty() || it.storePassword.isNotEmpty() ||
            it.keyAlias.isNotEmpty() || it.keyPassword.isNotEmpty() }) {
        val missing = listOf(
            "AIVANCE_STORE_FILE" to fromEnv.storeFilePath,
            "AIVANCE_STORE_PASSWORD" to fromEnv.storePassword,
            "AIVANCE_KEY_ALIAS" to fromEnv.keyAlias,
            "AIVANCE_KEY_PASSWORD" to fromEnv.keyPassword,
        ).filter { (_, value) -> value.isEmpty() }.map { (name, _) -> name }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Incomplete release signing configuration. Missing: ${missing.joinToString(", ")}. " +
                    "Set all four, or unset all four to fall back to keystore.properties."
            )
        }
        return fromEnv
    }

    val propsFile = rootProject.file("keystore.properties")
    if (!propsFile.exists()) return null
    val props = Properties().apply { propsFile.inputStream().use { load(it) } }
    val path = props.getProperty("storeFile").orEmpty()
    if (path.isEmpty()) {
        throw GradleException(
            "keystore.properties exists but has no storeFile entry. " +
                "Expected storeFile / storePassword / keyAlias / keyPassword."
        )
    }
    return SigningCredentials(
        // `storeFile` is commonly written relative to the repository root;
        // `Project.file` resolves that and passes absolute paths through
        // unchanged. (`File(path)` is not used here — in a Gradle Kotlin DSL
        // script the bare name `File` does not resolve to `java.io.File`.)
        storeFilePath = rootProject.file(path).absolutePath,
        storePassword = props.getProperty("storePassword").orEmpty(),
        keyAlias = props.getProperty("keyAlias").orEmpty(),
        keyPassword = props.getProperty("keyPassword").orEmpty(),
    )
}

val signingCredentials = readSigningCredentials()
val signingStoreFile = signingCredentials?.let { rootProject.file(it.storeFilePath) }
val signingConfigured = signingCredentials != null && signingStoreFile?.exists() == true
// `assembleRelease` on a machine with no keystore is a legitimate way to
// exercise R8 (that is what the PR CI gate does), so an unsigned release build
// is allowed by default. The release workflow passes
// `-Paivance.requireSigning=true`, which turns the silent unsigned output into
// a hard failure — a release must never be produced unsigned by accident.
val requireSigning = providers.gradleProperty("aivance.requireSigning").orNull == "true"
if (requireSigning && !signingConfigured) {
    val detail = if (signingStoreFile == null) {
        "no AIVANCE_* environment variables and no keystore.properties at the repository root"
    } else {
        "keystore file not found at ${signingStoreFile.path}"
    }
    throw GradleException(
        "aivance.requireSigning=true but the release build cannot be signed ($detail). " +
            "Set AIVANCE_STORE_FILE, AIVANCE_STORE_PASSWORD, AIVANCE_KEY_ALIAS and " +
            "AIVANCE_KEY_PASSWORD, or provide keystore.properties at the repository root."
    )
}

android {
    namespace = "com.bangersoul.aivance"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.bangersoul.aivance"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (signingConfigured) {
            create("release") {
                val credentials = requireNotNull(signingCredentials) {
                    "signingConfigured was true but no credentials were resolved"
                }
                storeFile = requireNotNull(signingStoreFile)
                storePassword = credentials.storePassword
                keyAlias = credentials.keyAlias
                keyPassword = credentials.keyPassword
            }
        }
    }

    buildTypes {
        release {
            // Null when no keystore is configured, which yields an unsigned
            // release build. `-Paivance.requireSigning=true` (used by the
            // release workflow) fails the build before we ever get here.
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            optimization {
                enable = true
            }
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }

            // `ProviderIntegrationTest` reads these five fields, and
            // androidTest compiles against the release variant now (see
            // `testOptions` below). So release has to *declare* them — but it
            // must never carry a real value: a provider key baked into the
            // release APK belongs to anyone who unzips it.
            //
            // Hardcoding empty literals here is what makes that a compiler-
            // enforced invariant rather than a convention. A stray key in
            // local.properties cannot reach the release variant, because this
            // block never reads it. The live-API tests then report as *skipped*
            // via their existing `assumeTrue(...isNotBlank())` guards, instead
            // of silently passing with no key and no coverage.
            buildConfigField("String", "APIFY_API_KEY", "\"\"")
            buildConfigField("String", "GROQ_API_KEY", "\"\"")
            buildConfigField("String", "GEMINI_API_KEY", "\"\"")
            buildConfigField("String", "HUNTER_API_KEY", "\"\"")
            // Not a credential, but release still never talks to third-party
            // production endpoints from a test run.
            buildConfigField("boolean", "RUN_LIVE_API_TESTS", "false")
        }
        debug {
            isMinifyEnabled = false
            // By default the debug build installs as a distinct app id
            // (com.bangersoul.aivance.debug). Pass
            // -Paivance.useRegisteredAppId=true to build the debug variant
            // under the already-registered release applicationId
            // (com.bangersoul.aivance) so a single Firebase app entry — with
            // the debug keystore SHA-1 added — covers Google sign-in on the
            // emulator without registering a separate .debug app.
            val useRegisteredAppId =
                providers.gradleProperty("aivance.useRegisteredAppId").orNull == "true"
            if (!useRegisteredAppId) {
                applicationIdSuffix = ".debug"
            }
            versionNameSuffix = "-debug"

            // Phase 4 integration-test keys (see local.properties). Debug-only:
            // androidTest runs against the debug variant, and release APKs must
            // never embed real provider credentials in BuildConfig.
            buildConfigField("String", "APIFY_API_KEY", "\"${integrationApiKey("apifyApiKey")}\"")
            buildConfigField("String", "GROQ_API_KEY", "\"${integrationApiKey("groqApiKey")}\"")
            buildConfigField("String", "GEMINI_API_KEY", "\"${integrationApiKey("geminiApiKey")}\"")
            buildConfigField("String", "HUNTER_API_KEY", "\"${integrationApiKey("hunterApiKey")}\"")
            // RemoteOK and Remotive need no key, so there is no key to gate
            // their live-API tests on. This opt-in keeps them runnable locally
            // without letting a third party's response shape decide whether CI
            // is green. Not a credential, so it is safe in the debug variant.
            buildConfigField(
                "boolean",
                "RUN_LIVE_API_TESTS",
                "(${integrationApiKey("runLiveApiTests").equals("true", ignoreCase = true)})",
            )
        }
    }

    // The app ships ~40 MB of native libs per ABI (on-device LLM inference
    // engine + ML Kit OCR), so a universal APK is ~165 MB. Splitting by ABI
    // gives each device only its own slice, and the AAB does the same for Play.
    // AGP forbids split APKs and a bundle in one build, so disable splits for
    // bundle-only runs: ./gradlew :app:bundleRelease -Paivance.disableAbiSplits=true
    val disableAbiSplits = providers.gradleProperty("aivance.disableAbiSplits").orNull == "true"
    splits {
        abi {
            isEnable = !disableAbiSplits
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = false
        }
    }

    // Play distributes per-ABI slices automatically from the AAB (AGP 9
    // builds ABI splits into bundles by default; density splits are gone).

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // Run the instrumented suite against the *release* variant, so R8 and
        // resource shrinking are genuinely exercised.
        //
        // A green `connectedDebugAndroidTest` proves nothing about the build
        // that ships: debug sets `isMinifyEnabled = false`, so every prior run
        // tested unshrunk bytecode. R8 removes classes by whole-file analysis,
        // and the classic casualties here are exactly what this app leans on —
        // ~220 `@Serializable` types, Hilt-generated components, Room DAO impls
        // and Retrofit interfaces. Those break at *runtime* with
        // SerializationException / ClassNotFoundException, long after every
        // compile-time check has passed. Compiling release in CI proves R8 does
        // not error; only running the shrunken APK proves the app still works.
        //
        // Release is signed, so the androidTest APK must be signed with the
        // same key — `signingConfigs["release"]` is what makes that automatic.
        // Without a keystore this task cannot install anything, which is why
        // the emulator workflow provisions one before it runs.
        defaultConfig {
            testBuildType = "release"
        }
        unitTests {
            // AGP 9.x JVM unit tests throw "Method ... not mocked" when code touches
            // android.util.Log / Build.* (e.g. DownloadManager/UploadManager init blocks).
            isReturnDefaultValues = true
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:sdk"))
    implementation(project(":core:util"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ai-providers"))
    implementation(project(":core:job-providers"))
    implementation(project(":core:enrichment-providers"))

    implementation(project(":feature:dashboard"))
    implementation(project(":feature:resume"))
    implementation(project(":feature:ats"))
    implementation(project(":feature:coverletter"))
    implementation(project(":feature:tracker"))
    implementation(project(":feature:interview"))
    implementation(project(":feature:jobs"))
    implementation(project(":feature:profile"))
    implementation(project(":feature:recruiter"))
    implementation(project(":feature:analytics"))
    implementation(project(":feature:assistant"))

    implementation(project(":navigation"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.compose.adaptive.layout)
    implementation(libs.androidx.compose.adaptive.navigation3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.work)
    implementation(libs.hilt.android)
    implementation(libs.timber)
    implementation(libs.coil.compose)
    implementation(libs.retrofit)
    implementation(libs.converter.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.accompanist.permissions)
    implementation(libs.play.services.location)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.core)
    implementation(libs.material)
    implementation(libs.androidx.profileinstaller)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    // Real-API integration tests (Phase 4 STEP 3) — kotlinx-coroutines-test for runTest.
    androidTestImplementation(libs.kotlinx.coroutines.test)
    // Was `debugImplementation`, which is the wrong scope twice over: it merged
    // the Compose test host activity into the *shipped* debug APK, and it is not
    // on the androidTest classpath for any other variant — so with
    // `testBuildType = "release"` a Compose UI test would have had no host
    // activity to bind to. This artifact exists to serve instrumented tests, so
    // `androidTestImplementation` is the correct scope.
    androidTestImplementation(libs.androidx.compose.ui.test.manifest)

    // Preview tooling genuinely is a debug-only concern for the app itself, so
    // this one stays where it is.
    debugImplementation(libs.androidx.compose.ui.tooling)

    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.work.compiler)
}
