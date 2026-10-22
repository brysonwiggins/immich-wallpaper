plugins {
    id("com.android.application") // version comes from the root buildscript classpath
    alias(libs.plugins.kotlin.compose)
    id("io.github.takahirom.roborazzi") // version comes from the root buildscript classpath
}

android {
    namespace = "com.wiggyserver.immichwallpaper"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        // The Kotlin package (namespace) stays com.wiggyserver.immichwallpaper; only the app id is public.
        applicationId = "com.wiggyserver.wallpaperswithimmich"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 13
        versionName = "1.0.0"
    }

    // Two builds from one codebase: "play" has the Google Play Billing tip jar;
    // "foss" (GitHub releases, F-Droid) has no proprietary libraries and links to donation pages.
    flavorDimensions += "store"
    productFlavors {
        create("foss") { dimension = "store" }
        create("play") { dimension = "store" }
    }

    // Committed debug key so CI-built test APKs update in place (keeping settings and queue).
    // The release key is never committed: it comes from environment variables (see the Release workflow).
    // Without them, release builds are signed with the debug key so they can still be tested.
    val releaseStore = providers.environmentVariable("RELEASE_STORE_FILE").orNull
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = providers.environmentVariable("RELEASE_STORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (releaseStore != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/versions/9/OSGI-INF/MANIFEST.MF")
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.jvmArgs(
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
            )
        }
    }
}

dependencies {
    implementation(project(":render-android"))
    implementation(project(":presentation"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.work.runtime)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    "playImplementation"(libs.play.billing)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}

// Screenshot goldens live with the tests. CI records them on request (commit message
// containing "[record screenshots]") and verifies them on every other push.
roborazzi {
    outputDir.set(file("src/test/screenshots"))
}
