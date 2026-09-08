package dev.sk2andy.materialbrowser.browser

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Compile-time gate for Candy's selectable Android browser engines. */
class AndroidBrowserEngineArchitectureTest {
    @Test
    fun `build includes Gecko and AndroidX WebKit without a compile time engine flag`() {
        val buildScript = source("app/build.gradle.kts")

        assertTrue(buildScript.contains("org.mozilla.geckoview:geckoview"))
        assertTrue(buildScript.contains("androidx.webkit:webkit"))
        assertFalse(buildScript.contains("USE_GECKO_ENGINE"))
    }

    @Test
    fun `system WebView implementation stays inside its adapter package`() {
        val webViewImport = Regex("(?m)^import (?:android|androidx)\\.webkit\\.")
        val importsOutsideAdapter = productionKotlinFiles()
            .filter { file -> webViewImport.containsMatchIn(file.readText()) }
            .filterNot { file ->
                file.invariantSeparatorsPath.contains("/browser/systemwebview/") ||
                    file.invariantSeparatorsPath.contains("/browser/userscript/")
            }
            .map { file -> file.relativeTo(repositoryRoot).invariantSeparatorsPath }
            .sorted()
            .toList()

        assertTrue("WebView imports escaped adapter edges: $importsOutsideAdapter", importsOutsideAdapter.isEmpty())
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
