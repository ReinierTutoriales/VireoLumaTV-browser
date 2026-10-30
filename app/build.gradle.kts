import java.io.File
import javax.inject.Inject
import org.gradle.process.ExecOperations

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

/** ABIs shipped with the Rust adblock engine. 32-bit x86 Android TV hardware does not exist. */
val rustAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")

/** Compiles native/adblock-jni (Brave's adblock-rust behind JNI) with the NDK toolchain. */
abstract class BuildRustAdblock : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Internal
    abstract val crateDir: DirectoryProperty

    /** Unset when the SDK has no NDK: the host build does not need one. */
    @get:Internal
    abstract val ndkDirectory: DirectoryProperty

    // Track the selected toolchain without snapshotting the entire NDK directory.
    @get:Input
    @get:Optional
    abstract val ndkVersion: Property<String>

    @get:Input
    abstract val minSdk: Property<Int>

    @get:Input
    abstract val abis: ListProperty<String>

    /** Android libraries by ABI; when [hostLibrary] is set, only the host build runs. */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val hostLibrary: Property<Boolean>

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun build() {
        val script = crateDir.file("build.sh").get().asFile.absolutePath
        val out = outputDir.get().asFile.absolutePath
        val args = if (hostLibrary.get()) listOf(script, "-", "/dev/null", out, minSdk.get().toString())
            else listOf(script, ndk(), out, "-", minSdk.get().toString()) + abis.get()
        execOperations.exec { commandLine(args) }
    }

    /** The NDK AGP resolves, else the one preinstalled on CI runners. */
    private fun ndk(): String = ndkDirectory.orNull?.asFile?.absolutePath
        ?: listOf("ANDROID_NDK_HOME", "ANDROID_NDK_LATEST_HOME", "ANDROID_NDK_ROOT")
            .firstNotNullOfOrNull { name -> System.getenv(name)?.takeIf { File(it).isDirectory } }
        ?: throw GradleException("NDK not found: install the version in gradle/libs.versions.toml (android-ndk) with sdkmanager or set ANDROID_NDK_HOME")
}

android {
    namespace = "com.reiniertutoriales.vireolumatv"
    compileSdk {
        version = release(libs.versions.android.compileSdk.get().toInt()) {
            minorApiLevel = libs.versions.android.compileSdkMinor.get().toInt()
        }
    }
    ndkVersion = libs.versions.android.ndk.get()

    defaultConfig {
        applicationId = "com.reiniertutoriales.vireolumatv"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 97
        versionName = "2.0.3"
    }

    // Release keys come from the environment (CI secrets); without them the release is unsigned,
    // or signed with the debug key when CI asks for an installable test build.
    signingConfigs {
        create("release") {
            System.getenv("KEYSTORE_PATH")?.let { path ->
                storeFile = file(path)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            // Resources are reached only through R, never by name, so unused ones can go.
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = when {
                System.getenv("KEYSTORE_PATH") != null -> signingConfigs.getByName("release")
                System.getenv("CI_SIGN_RELEASE_WITH_DEBUG_KEY") == "true" -> signingConfigs.getByName("debug")
                else -> null
            }
        }
    }

    splits {
        abi {
            // One APK per ABI: a 32-bit TV installs about 4 MB instead of a universal 10.7 MB.
            isEnable = true
            reset()
            include(*rustAbis.toTypedArray())
            isUniversalApk = false
        }
    }

    // Builds that update themselves from this repository's releases, and store builds that must not.
    flavorDimensions += "distribution"
    productFlavors {
        create("generic") {
            dimension = "distribution"
            buildConfigField("Boolean", "BUILT_IN_AUTO_UPDATE", "true")
        }
        create("google") {
            dimension = "distribution"
            buildConfigField("Boolean", "BUILT_IN_AUTO_UPDATE", "false")
        }
        create("foss") {
            dimension = "distribution"
            applicationIdSuffix = ".foss"
            buildConfigField("Boolean", "BUILT_IN_AUTO_UPDATE", "false")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    testOptions.unitTests.isIncludeAndroidResources = true
}

// Room's processor: the exported schemas in schemas/ are how schema changes are caught.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}

val rustCrate = rootProject.layout.projectDirectory.dir("native/adblock-jni")
fun BuildRustAdblock.configureCrate() {
    crateDir.set(rustCrate)
    sources.from(rustCrate.file("Cargo.toml"), rustCrate.file("Cargo.lock"), rustCrate.file("build.sh"), rustCrate.dir("src"))
    val ndk = androidComponents.sdkComponents.ndkDirectory
    ndkDirectory.set(providers.provider { runCatching { ndk.get() }.getOrNull() })
    minSdk.set(libs.versions.android.minSdk.get().toInt())
    abis.set(rustAbis)
}

val buildRustAdblock = tasks.register<BuildRustAdblock>("buildRustAdblock") {
    configureCrate()
    hostLibrary.set(false)
    ndkVersion.set(libs.versions.android.ndk)
    outputDir.set(layout.buildDirectory.dir("rust/jniLibs"))
}

// Host build of the same engine: JVM unit tests load it instead of a fake.
val buildRustAdblockHost = tasks.register<BuildRustAdblock>("buildRustAdblockHost") {
    configureCrate()
    hostLibrary.set(true)
    outputDir.set(layout.buildDirectory.dir("rust/host"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(buildRustAdblock, BuildRustAdblock::outputDir)
    }
}

tasks.withType<Test>().configureEach {
    dependsOn(buildRustAdblockHost)
    val hostLib = buildRustAdblockHost.flatMap { it.outputDir.file("libvireoadblock.so") }
    inputs.files(hostLib)
    doFirst { systemProperty("vireo.adblock.hostLib", hostLib.get().asFile.absolutePath) }
}
