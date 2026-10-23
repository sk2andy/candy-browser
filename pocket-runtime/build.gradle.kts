plugins {
    id("com.android.library")
}

val onnxNative by configurations.creating {
    isTransitive = false
}

val unpackOnnxRuntime = tasks.register<Sync>("unpackOnnxRuntime") {
    from(provider { onnxNative.map { zipTree(it) } })
    include("headers/**", "jni/**")
    into(layout.buildDirectory.dir("onnxruntime"))
}

android {
    namespace = "dev.sk2andy.materialbrowser.pocket"
    compileSdk = 37
    ndkVersion = "27.2.12479018"

    defaultConfig {
        minSdk = 33
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                arguments += "-DONNX_RUNTIME_ROOT=${layout.buildDirectory.dir("onnxruntime").get().asFile}"
                arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
                arguments += "-DANDROID_STL=c++_static"
                targets += "candy_pocket"
                cppFlags += listOf("-std=c++17", "-O2")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // ONNX Runtime is already packaged by its AAR; only package our JNI adapter here.
    packaging.jniLibs.excludes += "**/libonnxruntime.so"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.configureEach {
    if (name.startsWith("configureCMake") || name.startsWith("buildCMake")) {
        dependsOn(unpackOnnxRuntime)
    }
}

dependencies {
    onnxNative("com.microsoft.onnxruntime:onnxruntime-android:1.24.3@aar")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.24.3")
}
