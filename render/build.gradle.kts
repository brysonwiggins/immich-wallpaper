plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
}

// Slide rendering: the drawing algorithm plus a java.awt backend for golden tests. The Android
// Bitmap backend implements RasterBackend in an Android module.
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(project(":data"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // ./gradlew :render:test -Pgolden.update=true rewrites the golden images.
    systemProperty("golden.update", providers.gradleProperty("golden.update").getOrElse("false"))
    systemProperty("golden.dir", layout.projectDirectory.dir("src/test/resources/golden").asFile.absolutePath)
    systemProperty("java.awt.headless", "true")
}

kover {
    reports {
        verify {
            rule { minBound(85) }
        }
    }
}

tasks.named("check") { dependsOn("koverVerify") }
