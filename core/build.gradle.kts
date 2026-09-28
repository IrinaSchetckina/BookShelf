import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()
    
    jvm()
    
    js {
        browser()
    }
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }
    
    android {
       namespace = "ua.readshelf.core"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
    }
    
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            // api: LocalDate appears in public domain signatures.
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

// Mutation testing runs pitest's command line directly instead of the Gradle plugin: that plugin
// only creates its task when the java plugin is applied, which a KMP module does not do, and
// making it apply would change how :core builds. JUnit 4 needs no pitest plugin — kotlin-test
// resolves to kotlin-test-junit on the jvm target, which pitest supports natively.
//
// The score is 98% (61 of 62), not 100%, because of one equivalent mutant: CONDITIONALS_BOUNDARY
// flips "<" to "<=" inside the inlined maxOfOrNull that bookmarkOf uses, and both pick the same
// maximum. No test can kill it. Our own code is at 41 of 41. The threshold below is therefore a
// guard against regressions, not a target to reach.
val pitestCli: Configuration by configurations.creating

dependencies {
    pitestCli("org.pitest:pitest-command-line:1.30.0")
}

tasks.register<JavaExec>("pitest") {
    group = "verification"
    description = "Runs mutation testing over the reading domain of the jvm target."
    dependsOn("jvmTestClasses")

    classpath = pitestCli
    mainClass.set("org.pitest.mutationtest.commandline.MutationCoverageReport")

    // A FileCollection survives the configuration cache; a Configuration provider does not.
    val mutationClasspath: FileCollection = objects.fileCollection().from(
        layout.buildDirectory.dir("classes/kotlin/jvm/main"),
        layout.buildDirectory.dir("classes/kotlin/jvm/test"),
        configurations.named("jvmTestRuntimeClasspath"),
    )
    val reportDir = layout.buildDirectory.dir("reports/pitest").get().asFile
    val sources = layout.projectDirectory.dir("src/commonMain/kotlin").asFile

    argumentProviders.add(
        CommandLineArgumentProvider {
            // pitest separates path lists with commas, not with the platform path separator.
            val classPath = mutationClasspath.files.joinToString(",") { it.absolutePath }
            listOf(
                "--classPath", classPath,
                // The logic classes only. Data models are left out on purpose: mutating a
                // generated equals/hashCode/copy produces noise, not a signal about our tests.
                "--targetClasses",
                listOf(
                    "ua.readshelf.domain.reading.ReadingStreakKt",
                    "ua.readshelf.domain.reading.ReadingStatsKt",
                    "ua.readshelf.domain.reading.ReadingDay",
                ).joinToString(","),
                "--targetTests", "ua.readshelf.domain.reading.*",
                "--sourceDirs", sources.absolutePath,
                "--reportDir", reportDir.absolutePath,
                "--outputFormats", "HTML,XML",
                "--timestampedReports", "false",
                "--threads", "4",
                "--mutationThreshold", "95",
            )
        },
    )
}