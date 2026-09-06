package dev.sk2andy.materialbrowser.browser

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Compile-time migration gate: Candy's Android product path is Gecko-only. */
class GeckoOnlyBrowserEngineArchitectureTest {
    @Test
    fun `all Android distributions keep Gecko enabled`() {
        val buildScript = source("app/build.gradle.kts")

        assertTrue(
            buildScript.contains(
                "buildConfigField(\"boolean\", \"USE_GECKO_ENGINE\", \"true\")",
            ),
        )
        assertFalse(buildScript.contains("\"USE_GECKO_ENGINE\", \"false\""))
    }

    @Test
    fun `production Kotlin has no Android WebView imports`() {
        val webViewImport = Regex("(?m)^import (?:android|androidx)\\.webkit\\.")

        val imports = productionKotlinFiles()
            .filter { file -> webViewImport.containsMatchIn(file.readText()) }
            .map { file -> file.relativeTo(repositoryRoot).invariantSeparatorsPath }
            .sorted()
            .toList()

        assertTrue("Legacy WebView imports remain: $imports", imports.isEmpty())
    }

    @Test
    fun `Android build has no AndroidX WebKit dependency`() {
        val buildScript = source("app/build.gradle.kts")

        assertFalse(buildScript.contains("androidx.webkit:webkit"))
    }

    @Test
    fun `native Gecko trust uses the explicit build channel`() {
        val settings = source(
            "app/src/main/java/dev/sk2andy/materialbrowser/browser/gecko/GeckoRuntimeSettingsFactory.kt",
        )
        val runtime = source(
            "app/src/main/java/dev/sk2andy/materialbrowser/browser/gecko/GeckoViewRuntimeHandle.kt",
        )

        assertTrue(settings.contains("trustUserCertificates: Boolean = BuildConfig.TRUST_USER_CERTIFICATES"))
        assertTrue(settings.contains(".enterpriseRootsEnabled(trustUserCertificates)"))
        assertTrue(runtime.contains("GeckoRuntimeSettingsFactory.create(contentBlocking)"))
    }

    private fun source(relativePath: String): String {
        val file = repositoryRoot.resolve(relativePath)
        assertTrue("Missing architecture source: $relativePath", file.isFile)
        return file.readText()
    }

    private val repositoryRoot: File by lazy {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        generateSequence(File(workingDirectory).absoluteFile, File::getParentFile)
            .firstOrNull { candidate -> candidate.resolve("app/build.gradle.kts").isFile }
            ?: error("Could not locate Candy repository root")
    }

    private fun productionKotlinFiles(): Sequence<File> =
        listOf("main", "full", "foss", "debug")
            .asSequence()
            .map { sourceSet -> repositoryRoot.resolve("app/src/$sourceSet/java") }
            .filter(File::isDirectory)
            .flatMap(File::walkTopDown)
            .filter { file -> file.isFile && file.extension == "kt" }
}
