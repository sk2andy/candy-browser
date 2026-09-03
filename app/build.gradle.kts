import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskAction

abstract class GenerateLauncherShortcutResources : DefaultTask() {
    @get:Input
    abstract val applicationId: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val xmlDirectory = outputDirectory.dir("xml").get().asFile
        xmlDirectory.mkdirs()
        xmlDirectory.resolve("shortcuts.xml").writeText(
            """<?xml version="1.0" encoding="utf-8"?>
<shortcuts xmlns:android="http://schemas.android.com/apk/res/android">
    <shortcut
        android:shortcutId="launcher_new_tab"
        android:enabled="true"
        android:icon="@mipmap/ic_shortcut_new_tab"
        android:shortcutShortLabel="@string/new_tab_title"
        android:shortcutLongLabel="@string/command_new_regular_tab_name">
        <intent
            android:action="dev.sk2andy.materialbrowser.action.NEW_TAB"
            android:targetPackage="${applicationId.get()}"
            android:targetClass="dev.sk2andy.materialbrowser.LauncherShortcutActivity" />
    </shortcut>
    <shortcut
        android:shortcutId="launcher_new_private_tab"
        android:enabled="true"
        android:icon="@mipmap/ic_shortcut_private_tab"
        android:shortcutShortLabel="@string/incognito"
        android:shortcutLongLabel="@string/command_new_incognito_tab_name">
        <intent
            android:action="dev.sk2andy.materialbrowser.action.NEW_PRIVATE_TAB"
            android:targetPackage="${applicationId.get()}"
            android:targetClass="dev.sk2andy.materialbrowser.LauncherShortcutActivity" />
    </shortcut>
</shortcuts>
""".trimIndent(),
            Charsets.UTF_8,
        )
    }
}

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
val candyReleaseNotesFile = providers.gradleProperty("candy.releaseNotesFile")
    .orElse(candyVersionName.map { version -> "release-notes/$version.md" })
val releaseNotesImageSyntax = Regex("""!\[([^]]+)]\(([^)]+)\)""")
val releaseNotesImageFiles = providers.provider {
    val notes = rootProject.file(candyReleaseNotesFile.get())
    if (!notes.isFile) {
        emptyList()
    } else {
        val prefix = "https://raw.githubusercontent.com/sk2andy/candy-browser/" +
            "v${candyVersionName.get()}/docs/screenshots/"
        releaseNotesImageSyntax.findAll(notes.readText(Charsets.UTF_8))
            .mapNotNull { match ->
                match.groupValues[2]
                    .takeIf { target -> target.startsWith(prefix) }
                    ?.removePrefix(prefix)
                    ?.let { fileName -> rootProject.file("docs/screenshots/$fileName") }
            }
            .toList()
    }
}
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
        buildConfigField("String", "RELEASE_NOTES_VERSION", "\"${candyVersionName.get()}\"")

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
        getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/releaseNotes/assets"))
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

androidComponents {
    onVariants { variant ->
        val capitalizedVariantName = variant.name.replaceFirstChar(Char::uppercaseChar)
        val generateLauncherShortcuts = tasks.register<GenerateLauncherShortcutResources>(
            "generate${capitalizedVariantName}LauncherShortcutResources",
        ) {
            applicationId.set(variant.applicationId)
        }
        variant.sources.res?.addGeneratedSourceDirectory(
            generateLauncherShortcuts,
            GenerateLauncherShortcutResources::outputDirectory,
        )
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

val validateReleaseNotes by tasks.registering {
    group = "verification"
    description = "Validates the Markdown release notes packaged into the app."
    val releaseNotes = candyReleaseNotesFile.map(rootProject::file)
    inputs.property("releaseNotesVersion", candyVersionName)
    inputs.property("releaseNotesPath", candyReleaseNotesFile)
    inputs.file(releaseNotes)

    doLast {
        val version = candyVersionName.get()
        val expectedPath = "release-notes/$version.md"
        val configuredPath = candyReleaseNotesFile.get().replace('\\', '/')
        check(configuredPath == expectedPath) {
            "Release notes must use the version-matched path $expectedPath, got $configuredPath."
        }
        val file = releaseNotes.get()
        check(file.isFile) { "Release notes do not exist: ${file.absolutePath}" }
        val bytes = file.readBytes()
        check(bytes.isNotEmpty() && bytes.size <= 65_536) {
            "Release notes must contain 1..65536 UTF-8 bytes, got ${bytes.size}."
        }
        val content = bytes.toString(Charsets.UTF_8)
        check(!content.startsWith('\uFEFF') && content.lineSequence().firstOrNull()?.startsWith("# ") == true) {
            "Release notes must be UTF-8 without BOM and start with one '# ' heading."
        }
        val lines = content
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
        check(lines.all { line -> line.length <= 8_192 }) {
            "Release notes contain a line longer than 8192 characters."
        }
        check(lines.count(String::isNotBlank) <= 256) {
            "Release notes must contain at most 256 non-empty lines."
        }
        var codeBlockOpen = false
        lines.forEach { line ->
            if (!codeBlockOpen && line.startsWith("```")) {
                codeBlockOpen = true
            } else if (codeBlockOpen && line == "```") {
                codeBlockOpen = false
            }
        }
        check(!codeBlockOpen) { "Release notes contain an unclosed fenced code block." }
        check(content.none { character ->
            character.code in 0..31 && character != '\n' && character != '\r' && character != '\t'
        }) {
            "Release notes contain unsupported control characters."
        }
        val imageMatches = releaseNotesImageSyntax.findAll(content).toList()
        check(imageMatches.size <= 2) { "Release notes support at most two screenshots." }
        val expectedImagePrefix = "https://raw.githubusercontent.com/sk2andy/candy-browser/" +
            "v$version/docs/screenshots/"
        val imageFiles = imageMatches.map { match ->
            val altText = match.groupValues[1].trim()
            val target = match.groupValues[2]
            check(altText.isNotEmpty()) { "Every release-note screenshot needs useful alt text." }
            check(target.startsWith(expectedImagePrefix)) {
                "Release-note screenshots must use tag-pinned URLs below $expectedImagePrefix."
            }
            val fileName = target.removePrefix(expectedImagePrefix)
            check(fileName.matches(Regex("""[A-Za-z0-9][A-Za-z0-9._-]*\.(png|jpe?g|webp)"""))) {
                "Unsupported release-note screenshot path: $fileName."
            }
            rootProject.file("docs/screenshots/$fileName").also { image ->
                check(image.isFile) { "Release-note screenshot does not exist: ${image.absolutePath}" }
                check(image.length() in 1..2_097_152) {
                    "Release-note screenshot must contain 1..2097152 bytes: ${image.absolutePath}."
                }
            }
        }
        check(imageFiles.sumOf { image -> image.length() } <= 4_194_304) {
            "Release-note screenshots exceed the 4 MiB packaged limit."
        }
    }
}

val generateReleaseNotesAsset by tasks.registering(Sync::class) {
    dependsOn(validateReleaseNotes)
    inputs.files(releaseNotesImageFiles)
    from(candyReleaseNotesFile.map(rootProject::file)) {
        rename { "candy_release_notes.md" }
    }
    from(releaseNotesImageFiles) {
        into("release-notes-images")
    }
    into(layout.buildDirectory.dir("generated/releaseNotes/assets"))
    duplicatesStrategy = DuplicatesStrategy.FAIL
}

tasks.named("preBuild").configure {
    dependsOn(generateCandySyncDeviceIconAsset)
    dependsOn(generateReleaseNotesAsset)
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
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
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
