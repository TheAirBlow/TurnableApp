import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
}

/** Writes the version the build was packaged as, since [APP_VERSION] feeds update checks. */
abstract class GenerateAppVersionTask : DefaultTask() {
    @get:Input abstract val version: Property<String>
    @get:Input abstract val dev: Property<Boolean>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val file = outputDir.get().file("ru/theairblow/turnable/service/AppVersion.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package ru.theairblow.turnable.service

            /** The version this build was packaged as. */
            const val APP_VERSION = "${version.get()}"

            /** True for builds that are not a clean release, such as a commit past a tag or a dirty tree. */
            const val IS_DEV_BUILD = ${dev.get()}
            """.trimIndent() + "\n",
        )
    }
}

// A release tag from CI, or a git-derived dev version; AppUpdate compares it against release tags
val appVersion = rootProject.extra["appVersion"] as String

val generateAppVersion = tasks.register<GenerateAppVersionTask>("generateAppVersion") {
    version.set(appVersion)
    dev.set(!Regex("""\d+\.\d+(\.\d+)?""").matches(appVersion))
    outputDir.set(layout.buildDirectory.dir("generated/appVersion/commonMain/kotlin"))
}

kotlin {
    jvm()

    // Configure the Android KMP target's JVM target via the android block below

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // Android configuration for KMP library (via the new plugin)
    android {
        namespace = "ru.theairblow.turnable.lib"
        compileSdk = libs.versions.android.compileSdk.get().toInt()

        // Set the JVM target for Kotlin/Java compilation for the Android target.
        // This is the recommended place to configure jvmTarget for the KMP Android plugin.
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        // Enable Android resource processing (opt-in). If you don't use Android resources
        // (src/androidMain/res) you can leave this disabled. Enable when adding res folders.
        androidResources {
            enable = true
        }

        // Opt-in examples (uncomment to enable host/device tests or Java compilation):
        // withHostTest { isIncludeAndroidResources = true }
        // withDeviceTest { instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"; execution = "HOST" }
        // withJava() // enable if you have Java sources under src/androidMain/java
    }

    sourceSets {
        commonMain.get().kotlin.srcDir(generateAppVersion)

        // Code shared by the two JVM-based targets (sockets, crypto, process management)
        val jvmCommon by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.bouncycastle.bcprov)
            }
        }
        androidMain.get().dependsOn(jvmCommon)
        jvmMain.get().dependsOn(jvmCommon)

        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.androidx.webkit)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.serialization.protobuf)
            implementation(libs.kotlinx.datetime)
            implementation(libs.material.icons.extended)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}


compose.desktop {
    application {
        mainClass = "ru.theairblow.turnable.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ru.theairblow.turnable"
            // Installers reject a 0.x major version
            packageVersion = Regex("""^(\d+)\.(\d+)(?:\.(\d+))?""").find(appVersion)!!.destructured
                .let { (major, minor, patch) -> "${if (major == "0") "1" else major}.$minor.${patch.ifEmpty { "0" }}" }
        }
    }
}
