plugins {
    id("com.android.library") // version comes from the root buildscript classpath
}

// android.graphics implementation of :render's RasterBackend. Tested with Robolectric native graphics.
android {
    namespace = "com.wiggyserver.immichwallpaper.render.android"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric's SDK 36 environment fakes FileDescriptor internals through jdk.internal.access.
            it.jvmArgs(
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
            )
        }
    }
}

dependencies {
    api(project(":render"))

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
}
