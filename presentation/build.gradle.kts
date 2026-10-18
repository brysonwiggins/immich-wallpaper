plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
}

// Pure-JVM screen logic: state holders for each screen, tested with fakes. The app
// module renders their state with Compose and wires them to the real repositories.
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(project(":data"))
    api(libs.kotlinx.coroutines.core)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
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
