import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseKeystorePropertiesFile = rootProject.file("keystore.properties")
val releaseKeystoreProperties = Properties().apply {
    if (releaseKeystorePropertiesFile.isFile) {
        releaseKeystorePropertiesFile.inputStream().use(::load)
    }
}

fun releaseSigningValue(propertyName: String, environmentVariable: String): String? =
    releaseKeystoreProperties.getProperty(propertyName)
        ?.takeIf(String::isNotBlank)
        ?: providers.environmentVariable(environmentVariable).orNull?.takeIf(String::isNotBlank)

val releaseSigningValues = mapOf(
    "storeFile" to releaseSigningValue("storeFile", "CANDY_RELEASE_KEYSTORE_PATH"),
    "storePassword" to releaseSigningValue("storePassword", "CANDY_RELEASE_STORE_PASSWORD"),
    "keyAlias" to releaseSigningValue("keyAlias", "CANDY_RELEASE_KEY_ALIAS"),
    "keyPassword" to releaseSigningValue("keyPassword", "CANDY_RELEASE_KEY_PASSWORD"),
)
val missingReleaseSigningValues = releaseSigningValues.filterValues { it == null }.keys
val hasReleaseSigning = missingReleaseSigningValues.isEmpty()
val candyVersionCode = providers.gradleProperty("candy.versionCode").orElse("1")
val candyVersionName = providers.gradleProperty("candy.versionName").orElse("0.1")

android {
    namespace = "dev.sk2andy.materialbrowser"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.sk2andy.materialbrowser"
        minSdk = 34
        targetSdk = 35
        versionCode = candyVersionCode.get().toInt()
        versionName = candyVersionName.get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(requireNotNull(releaseSigningValues["storeFile"]))
                storePassword = requireNotNull(releaseSigningValues["storePassword"])
                keyAlias = requireNotNull(releaseSigningValues["keyAlias"])
                keyPassword = requireNotNull(releaseSigningValues["keyPassword"])
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

val validateReleaseSigning by tasks.registering {
    group = "verification"
    description = "Checks that release signing credentials and the keystore are available."

    doLast {
        check(missingReleaseSigningValues.isEmpty()) {
            "Missing release signing values: ${missingReleaseSigningValues.sorted().joinToString()}. " +
                "Configure keystore.properties or the CANDY_RELEASE_* environment variables."
        }

        val keystoreFile = rootProject.file(requireNotNull(releaseSigningValues["storeFile"]))
        check(keystoreFile.isFile) {
            "Release keystore does not exist: ${keystoreFile.absolutePath}"
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(validateReleaseSigning)
}

dependencies {
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.webkit:webkit:1.13.0")
    implementation("com.google.guava:guava:33.2.1-android")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
