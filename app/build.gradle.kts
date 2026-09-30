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
        create("release") {
            val keystoreFile = file("../keystore.jks")
            val storePwd = System.getenv("AIVANCE_STORE_PASSWORD")
            val keyAl = System.getenv("AIVANCE_KEY_ALIAS")
            val keyPwd = System.getenv("AIVANCE_KEY_PASSWORD")
            if (keystoreFile.exists() && storePwd != null && keyAl != null && keyPwd != null) {
                storeFile = keystoreFile
                storePassword = storePwd
                keyAlias = keyAl
                keyPassword = keyPwd
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")?.takeIf {
                it.storeFile?.exists() == true
            }
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

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.work.compiler)
}
