plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.reiniertutoriales.vireolumatv.baselineprofile"
    compileSdk {
        version = release(libs.versions.android.compileSdk.get().toInt()) {
            minorApiLevel = libs.versions.android.compileSdkMinor.get().toInt()
        }
    }

    defaultConfig {
        // Baseline profiles are collected on Android 9+ without root.
        minSdk = 28
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // CI runs on emulators: their timings compare variants of one build, not real TV hardware.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    targetProjectPath = ":app"
    // The app merges every generated profile into src/main (mergeIntoMain), so one profile serves all
    // distributions. google is left out: it shares generic's applicationId, and installing both on the
    // same managed device made the google run start without its Room classes (AppDatabase_Impl).
    flavorDimensions += "distribution"
    productFlavors {
        // Each flavor tells the generator which installed package to profile (foss adds ".foss").
        create("generic") {
            dimension = "distribution"
            testInstrumentationRunnerArguments["targetAppId"] = "com.reiniertutoriales.vireolumatv"
        }
        create("foss") {
            dimension = "distribution"
            testInstrumentationRunnerArguments["targetAppId"] = "com.reiniertutoriales.vireolumatv.foss"
        }
    }

    testOptions.managedDevices.localDevices {
        create("pixel6Api34") {
            device = "Pixel 6"
            apiLevel = 34
            systemImageSource = "aosp-atd"
        }
        // Full AOSP system image for Macrobenchmark frame timing.
        create("pixel6Api34Aosp") {
            device = "Pixel 6"
            apiLevel = 34
            systemImageSource = "aosp"
        }
    }
}

baselineProfile {
    managedDevices += "pixel6Api34"
    useConnectedDevices = false
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.benchmark.macro.junit4)
}
