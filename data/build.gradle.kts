plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

// Pure-JVM data layer: Immich client, file store, repositories. Android persistence (Room,
// DataStore, Keystore) implements this module's store interfaces in the app module.
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(project(":core"))
    api(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.okhttp.mockwebserver)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test { useJUnitPlatform() }

kover {
    reports {
        verify {
            rule { minBound(90) }
        }
    }
}

tasks.named("check") { dependsOn("koverVerify") }
