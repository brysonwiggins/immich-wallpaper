plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
}

// Pure-Kotlin domain logic (no Android): layout planning, pairing, cropping, queue, swap coordination.
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test { useJUnitPlatform() }

// :core coverage must stay >= 90%: `check` fails below that.
kover {
    reports {
        verify {
            rule { minBound(90) }
        }
    }
}

tasks.named("check") { dependsOn("koverVerify") }
