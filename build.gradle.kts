// AGP and the Kotlin plugins must share the root build classloader (KGP references AGP types).
// AGP goes on the root classpath only when Android modules are included, so JVM-only builds
// (-Pimmichwall.jvmOnly=true) never need to resolve it from Google Maven.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    if (findProperty("immichwall.jvmOnly") != "true") {
        dependencies {
            classpath(libs.android.gradle.plugin)
            classpath(libs.roborazzi.gradle.plugin) // screenshot tests in :app; references AGP types too
        }
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kover) apply false
}

// Print full stack traces for failing tests (CI logs are the only window into Android test failures).
subprojects {
    tasks.withType<Test>().configureEach {
        testLogging {
            events("failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showCauses = true
            showStackTraces = true
        }
    }
}
