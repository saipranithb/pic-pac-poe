plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val releaseApplicationId = "com.thevaguebox.probabilistictictactoe"
val releaseCompileSdk = 36
val releaseTargetSdk = 36
val releaseVersionCode = 5
val releaseVersionName = "2.0.0"

val uploadKeystorePath = providers.environmentVariable("ANDROID_UPLOAD_KEYSTORE_PATH").orNull
val uploadKeyAlias = providers.environmentVariable("ANDROID_UPLOAD_KEY_ALIAS").orNull
val uploadKeyPassword = providers.environmentVariable("ANDROID_UPLOAD_KEY_PASSWORD").orNull
val uploadStorePassword = providers.environmentVariable("ANDROID_UPLOAD_STORE_PASSWORD").orNull
val uploadSigningValues = listOf(
    uploadKeystorePath,
    uploadKeyAlias,
    uploadKeyPassword,
    uploadStorePassword,
)
val suppliedUploadSigningValues = uploadSigningValues.count { !it.isNullOrBlank() }
check(suppliedUploadSigningValues == 0 || suppliedUploadSigningValues == uploadSigningValues.size) {
    "Release signing is only partially configured. Supply all ANDROID_UPLOAD_* values or none of them."
}
val uploadSigningConfigured = suppliedUploadSigningValues == uploadSigningValues.size

android {
    namespace = releaseApplicationId
    compileSdk = releaseCompileSdk

    defaultConfig {
        applicationId = releaseApplicationId
        minSdk = 24
        targetSdk = releaseTargetSdk
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (uploadSigningConfigured) {
            create("upload") {
                storeFile = file(requireNotNull(uploadKeystorePath))
                keyAlias = requireNotNull(uploadKeyAlias)
                keyPassword = requireNotNull(uploadKeyPassword)
                storePassword = requireNotNull(uploadStorePassword)
            }
        }
    }

    buildTypes {
        release {
            // AGP 8.8 is the newest plugin supported by the target Android Studio,
            // but its bundled R8 predates Kotlin 2.3 metadata support.
            isMinifyEnabled = false
            isShrinkResources = false
            if (uploadSigningConfigured) {
                signingConfig = signingConfigs.getByName("upload")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }

    testOptions { unitTests.isIncludeAndroidResources = true }

    lint {
        disable += setOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable")
    }
}

val verifyReleaseConfiguration by tasks.registering {
    group = "verification"
    description = "Verifies the immutable Play identity and 2.0.0 release coordinates."
    doLast {
        check(android.namespace == releaseApplicationId)
        check(android.compileSdk == releaseCompileSdk)
        check(android.defaultConfig.applicationId == releaseApplicationId)
        check(android.defaultConfig.targetSdk == releaseTargetSdk)
        check(android.defaultConfig.versionCode == releaseVersionCode)
        check(android.defaultConfig.versionName == releaseVersionName)
        check(!android.buildTypes.getByName("release").isMinifyEnabled) {
            "R8 must stay disabled on the AGP 8.8 / Kotlin 2.3 compatibility lane."
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":game-core"))
    implementation(project(":game-ai"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.material3)

    testImplementation(libs.junit)
    testImplementation(testFixtures(project(":game-core")))
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

val verifyNoInternetPermission by tasks.registering {
    group = "verification"
    description = "Fails when the merged release manifest requests Internet access."
    dependsOn("processReleaseMainManifest")
    doLast {
        val manifests = fileTree(layout.buildDirectory.dir("intermediates/merged_manifests/release")) {
            include("**/AndroidManifest.xml")
        }.files
        check(manifests.isNotEmpty()) { "Merged release manifest was not produced" }
        manifests.forEach { manifest ->
            check("android.permission.INTERNET" !in manifest.readText()) {
                "Offline guarantee violated by ${manifest.absolutePath}"
            }
        }
    }
}

tasks.matching { it.name == "check" }.configureEach {
    dependsOn(verifyNoInternetPermission)
    dependsOn(verifyReleaseConfiguration)
}
