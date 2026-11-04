import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.firebaseAppDistribution)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.ktlint)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.security.crypto)
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.messaging)
            implementation(libs.androidx.core.ktx)
        }

        androidUnitTest.dependencies {
            implementation(libs.robolectric)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.roborazzi.rule)
            implementation(libs.mockito.kotlin)
            implementation(libs.truth)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.multiplatform.settings.test)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.compose.ui.backhandler)

            // Ktor - HTTP Client
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.mock) // the debug menu's demo backend

            // Kotlinx Serialization
            implementation(libs.kotlinx.serialization.json)

            // Kotlinx Coroutines
            implementation(libs.kotlinx.coroutines.core)

            // Kotlinx DateTime
            implementation(libs.kotlinx.datetime)

            // Multiplatform Settings (Secure Storage)
            implementation(libs.multiplatform.settings.no.arg)

            // Kamel - Async Image Loading
            implementation(libs.kamel.image)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

// The one version for both apps: MARKETING_VERSION in iosApp/Configuration/Config.xcconfig.
val appVersionName =
    rootProject
        .file("iosApp/Configuration/Config.xcconfig")
        .readLines()
        .first { it.startsWith("MARKETING_VERSION=") }
        .substringAfter("=")
        .trim()

val versionBuild =
    if (project.hasProperty("versionBuild")) {
        project.property("versionBuild").toString().toInt()
    } else {
        System.getenv("VERSION_BUILD_NUMBER")?.toIntOrNull() ?: 1
    }

val appVersionCode = versionBuild

println("Building Shifa Doctor v$appVersionName (Build $appVersionCode)")

// Written by CI (google_play_deploy.yml); absent locally, so a local release build is debug-signed (see below).
val keystoreProperties =
    Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
    }

android {
    namespace = "shifa.doctor"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.shifa.doc"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersionName
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        if (!keystoreProperties.isEmpty) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        getByName("debug") {
            versionNameSuffix = "-debug"
            isDebuggable = true
        }

        getByName("release") {
            isMinifyEnabled = false
            // CI signs with the upload key; a local release build is debug-signed so it installs on a device.
            // Never upload one.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    // BuildConfig.DEBUG gates the HTTP logs and the debug menu (CLAUDE.md "Logging").
    buildFeatures {
        buildConfig = true
    }
    // Robolectric only sees the merged manifest and assets (where Compose resources live) with this.
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric screenshot tests are slow on one JVM; spread the test classes over half the cores.
        unitTests.all {
            it.maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
            // Screenshots show local times: one zone for every test JVM, whatever order the tests run in.
            it.systemProperty("user.timezone", "Asia/Tashkent")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

// Res lives in the app's own package (the default derives it from the root project name).
compose.resources {
    packageOfResClass = "shifa.doctor.generated.resources"
}

ktlint {
    version.set("1.1.1")
    android.set(true)
    ignoreFailures.set(false)
    // Compose resource accessors are generated into build/; only our sources are checked.
    filter { exclude { it.file.path.contains("/build/") } }
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
    }
}
