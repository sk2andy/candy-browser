import java.util.Properties
import org.gradle.api.file.DuplicatesStrategy

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

fun validatedApplicationIdSuffix(value: String): String {
    require(value.matches(Regex("""\.[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)*"""))) {
        "candy.localReleaseApplicationIdSuffix must start with '.' and contain valid ID segments."
    }
    return value
}

fun validatedAppLabel(value: String): String {
    require(value.isNotBlank() && value.none(Char::isISOControl)) {
        "candy.localReleaseAppLabel must contain visible text without control characters."
    }
    return value
}

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
val debugApplicationIdSuffix =
    providers.gradleProperty("candy.debugApplicationIdSuffix").orElse(".linkpeek")
val debugAppLabel = providers.gradleProperty("candy.debugAppLabel").orElse("Candy Link Peek")
val localReleaseApplicationIdSuffix =
    providers.gradleProperty("candy.localReleaseApplicationIdSuffix")
        .orElse(".local")
        .map(::validatedApplicationIdSuffix)
val localReleaseAppLabel =
    providers.gradleProperty("candy.localReleaseAppLabel")
        .orElse("Candy Browser Local")
        .map(::validatedAppLabel)

android {
    namespace = "dev.sk2andy.materialbrowser"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.sk2andy.materialbrowser"
        minSdk = 33
        targetSdk = 35
        versionCode = candyVersionCode.get().toInt()
        versionName = candyVersionName.get()
        manifestPlaceholders["appLabel"] = "@string/app_name"
        manifestPlaceholders["networkSecurityConfig"] = "@xml/network_security_config"
        buildConfigField("boolean", "ENABLE_GITHUB_UPDATES", "false")
        buildConfigField("boolean", "FOSS_DISTRIBUTION", "false")
        buildConfigField("boolean", "TRUST_USER_CERTIFICATES", "false")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("full") {
            dimension = "distribution"
        }

        create("foss") {
            dimension = "distribution"
            buildConfigField("boolean", "FOSS_DISTRIBUTION", "true")
        }
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
        debug {
            applicationIdSuffix = debugApplicationIdSuffix.get()
            versionNameSuffix = "-debug"
            manifestPlaceholders["appLabel"] = debugAppLabel.get()
        }

        release {
            isMinifyEnabled = true
            buildConfigField("boolean", "ENABLE_GITHUB_UPDATES", "true")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }

        create("localRelease") {
            initWith(getByName("release"))
            applicationIdSuffix = localReleaseApplicationIdSuffix.get()
            versionNameSuffix = "-local"
            manifestPlaceholders["appLabel"] = localReleaseAppLabel.get()
            buildConfigField("boolean", "ENABLE_GITHUB_UPDATES", "false")
            matchingFallbacks += listOf("release")
        }

        create("userCaDebug") {
            initWith(getByName("debug"))
            manifestPlaceholders["appLabel"] = "Candy Browser User CA Debug"
            manifestPlaceholders["networkSecurityConfig"] =
                "@xml/network_security_config_user_ca"
            buildConfigField("boolean", "TRUST_USER_CERTIFICATES", "true")
            matchingFallbacks += listOf("debug")
        }

        create("userCaRelease") {
            initWith(getByName("release"))
            manifestPlaceholders["appLabel"] = "Candy Browser User CA"
            manifestPlaceholders["networkSecurityConfig"] =
                "@xml/network_security_config_user_ca"
            buildConfigField("boolean", "TRUST_USER_CERTIFICATES", "true")
            matchingFallbacks += listOf("release")
        }
    }

    sourceSets {
        getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/candySyncIcons/assets"))
        getByName("userCaDebug").res.srcDir("src/userCa/res")
        getByName("userCaRelease").res.srcDir("src/userCa/res")
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

val generateCandySyncDeviceIconAsset by tasks.registering(Copy::class) {
    val catalog = rootProject.file("sync/protocol/device-icons-v1.json")
    inputs.file(catalog)
    from(catalog)
    into(layout.buildDirectory.dir("generated/candySyncIcons/assets"))
    rename { "candy_sync_device_icons_v1.json" }
    duplicatesStrategy = DuplicatesStrategy.FAIL
}

tasks.named("preBuild").configure {
    dependsOn(generateCandySyncDeviceIconAsset)
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

tasks.matching {
    it.name == "preFullReleaseBuild" ||
        it.name == "preFullLocalReleaseBuild" ||
        it.name == "preFullUserCaReleaseBuild"
}.configureEach {
    dependsOn(validateReleaseSigning)
}

val verifyFossReleaseDependencies by tasks.registering {
    group = "verification"
    description = "Rejects proprietary Google runtime dependencies from the FOSS release."

    doLast {
        val forbiddenGroups = listOf(
            "com.google.android.datatransport",
            "com.google.android.gms",
            "com.google.android.odml",
            "com.google.firebase",
            "com.google.mlkit",
        )
        val violations = configurations.getByName("fossReleaseRuntimeClasspath")
            .resolvedConfiguration
            .resolvedArtifacts
            .map { it.moduleVersion.id }
            .filter { module ->
                forbiddenGroups.any { group ->
                    module.group == group || module.group.startsWith("$group.")
                }
            }
            .map { it.toString() }
            .sorted()

        check(violations.isEmpty()) {
            "FOSS release contains forbidden Google runtime dependencies: " +
                violations.joinToString()
        }
    }
}

tasks.matching { it.name == "preFossReleaseBuild" }.configureEach {
    dependsOn(verifyFossReleaseDependencies)
}

dependencies {
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.webkit:webkit:1.16.0") {
        // WebKit is Java-only, but 1.16.0 publishes a Kotlin 2.1 stdlib dependency. Keep this
        // project on its compiler-compatible Kotlin 1.9 stdlib until the toolchain is upgraded.
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
    implementation("com.google.guava:guava:33.2.1-android")
    implementation("com.github.Dimezis:BlurView:version-3.2.0")
    implementation("com.lambdapioneer.argon2kt:argon2kt:1.6.0")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    "fullImplementation"("com.google.android.gms:play-services-code-scanner:16.1.0")
    "fullImplementation"("com.google.android.gms:play-services-cast-framework:21.4.0") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    add("userCaDebugImplementation", "androidx.compose.ui:ui-tooling")
    add("userCaDebugImplementation", "androidx.compose.ui:ui-test-manifest")
}
