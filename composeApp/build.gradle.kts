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
            packageVersion = providers.gradleProperty("appVersion").getOrElse("1.0.0")
                .split('.').let { (major, minor, patch) -> if (major == "0") "1.$minor.$patch" else "$major.$minor.$patch" }
        }
    }
}
