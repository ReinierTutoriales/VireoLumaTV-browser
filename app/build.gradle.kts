import java.io.File
import org.gradle.process.ExecOperations
import javax.inject.Inject

plugins {
    id("vireolumatv.android.application")
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

    defaultConfig {
        applicationId = "com.reiniertutoriales.vireolumatv"
        versionCode = 87
        versionName = "1.0.0"
        ndk {
            abiFilters += rustAbis
        }

        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf(
                    "room.incremental" to "true",
                    //used when AppDatabase @Database annotation exportSchema = true. Useful for migrations
                    "room.schemaLocation" to "$projectDir/schemas"
                )
            }
        }
    }

    ndkVersion = libs.versions.android.ndk.get()

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            isDebuggable = true
        }
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = true
            // Resources are only referenced through R (no getIdentifier), so shrinking is safe.
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val signReleaseWithDebugKey = System.getenv("CI_SIGN_RELEASE_WITH_DEBUG_KEY") == "true"
            signingConfig = when {
                System.getenv("KEYSTORE_PATH") != null -> signingConfigs.getByName("release")
                signReleaseWithDebugKey -> signingConfigs.getByName("debug")
                else -> null
            }
        }
    }

    splits {
        abi {
            isEnable = project.hasProperty("enableAbiSplits")
            reset()
            include(*rustAbis.toTypedArray())
            isUniversalApk = false
        }
    }

    flavorDimensions += listOf("appstore")
    productFlavors {
        create("generic") {
            dimension = "appstore"
            //disabled until this fork has its own update channel and release key
            buildConfigField("Boolean", "BUILT_IN_AUTO_UPDATE", "false")
        }
        create("google") {
            dimension = "appstore"
            //now auto-update violates Google Play policies
            buildConfigField("Boolean", "BUILT_IN_AUTO_UPDATE", "false")
        }
        create("foss") {
            dimension = "appstore"
            applicationIdSuffix = ".foss"
            buildConfigField("Boolean", "BUILT_IN_AUTO_UPDATE", "false")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":app:common"))

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.androidx.room.runtime)
    annotationProcessor(libs.androidx.room.compiler)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)

    implementation(libs.segmented.button)
    implementation(libs.pinned.section.listview)

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

val buildRustAdblock by tasks.registering(BuildRustAdblock::class) {
    configureCrate()
    hostLibrary.set(false)
    outputDir.set(layout.buildDirectory.dir("rust/jniLibs"))
}

// Host build of the same engine: JVM unit tests load it instead of a fake.
val buildRustAdblockHost by tasks.registering(BuildRustAdblock::class) {
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
