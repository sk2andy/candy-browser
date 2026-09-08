import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("com.android.library")
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose") version "1.8.2"
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    iosArm64()
    iosSimulatorArm64()

    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "CandyShared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

tasks.register<Exec>("iosSimulatorArm64IsolatedTest") {
    group = "verification"
    description = "Runs shared tests on the explicitly configured isolated iOS simulator."
    dependsOn("linkDebugTestIosSimulatorArm64")

    val deviceSet = providers.environmentVariable("CANDY_IOS_SIMULATOR_DEVICE_SET")
    val deviceId = providers.environmentVariable("CANDY_IOS_SIMULATOR_UDID")
    val testExecutable = layout.buildDirectory.file(
        "bin/iosSimulatorArm64/debugTest/test.kexe",
    )

    doFirst {
        check(deviceSet.isPresent) {
            "CANDY_IOS_SIMULATOR_DEVICE_SET must point to this session's dedicated device set."
        }
        check(deviceId.isPresent) {
            "CANDY_IOS_SIMULATOR_UDID must identify this session's dedicated simulator."
        }
        commandLine(
            "/usr/bin/xcrun",
            "simctl",
            "--set",
            deviceSet.get(),
            "spawn",
            "--standalone",
            deviceId.get(),
            testExecutable.get().asFile.absolutePath,
        )
    }
}

android {
    namespace = "dev.sk2andy.materialbrowser.shared"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        minSdk = 33
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
