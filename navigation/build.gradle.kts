plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.bangersoul.aivance.navigation"
    compileSdk = 37
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "com.bangersoul.aivance.navigation.HiltTestRunner"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:datastore"))
    implementation(project(":core:sdk"))
    implementation(project(":feature:dashboard"))
    implementation(project(":feature:resume"))
    implementation(project(":feature:ats"))
    implementation(project(":feature:coverletter"))
    implementation(project(":feature:interview"))
    implementation(project(":feature:jobs"))
    implementation(project(":feature:profile"))
    implementation(project(":feature:tracker"))
    implementation(project(":feature:recruiter"))
    implementation(project(":feature:analytics"))
    implementation(project(":feature:assistant"))
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.compose.adaptive.navigation3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.timber)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.android.testing.compiler)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.hilt.android.testing)
    // The nav-graph Hilt test renders the real app graph, which instantiates
    // ViewModels whose use cases pull in the entire data-layer DI (repositories,
    // database, providers). Aggregate the core modules hosting those bindings
    // into the isolated androidTest component so the graph resolves.
    androidTestImplementation(project(":core:data"))
    androidTestImplementation(project(":core:database"))
    androidTestImplementation(project(":core:datastore"))
    androidTestImplementation(project(":core:network"))
    androidTestImplementation(project(":core:ai-providers"))
    androidTestImplementation(project(":core:job-providers"))
    androidTestImplementation(project(":core:enrichment-providers"))
    kspAndroidTest(libs.hilt.android.testing.compiler)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.tooling.preview)
}
