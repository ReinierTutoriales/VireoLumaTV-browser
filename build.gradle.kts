buildscript {
    // AGP's built-in Kotlin support compiles with this Kotlin version.
    dependencies { classpath(libs.kotlin.gradle.plugin) }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ksp) apply false
}

// Preserve wrapper download retry policy when the distribution is regenerated.
tasks.wrapper {
    retries = 3
    retryBackOffMs = 1000
}
