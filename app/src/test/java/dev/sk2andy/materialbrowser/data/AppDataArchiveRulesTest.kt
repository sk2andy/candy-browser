package dev.sk2andy.materialbrowser.data

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppDataArchiveRulesTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `persistent roots exclude only disposable app directories`() {
        val dataDirectory = temporaryFolder.newFolder("data")
        listOf(
            "cache",
            "code_cache",
            "lib",
            "app_textures",
            AppDataArchiveRules.TRANSFER_STATE_DIRECTORY_NAME,
            "shared_prefs",
            "databases",
            "no_backup",
        ).forEach { name -> Files.createDirectory(dataDirectory.toPath().resolve(name)) }

        assertEquals(
            setOf("shared_prefs", "databases", "no_backup"),
            AppDataArchiveRules.persistentRootNames(dataDirectory),
        )
        assertFalse(AppDataArchiveRules.shouldExportTopLevel("cache"))
        assertFalse(
            AppDataArchiveRules.shouldExportTopLevel(
                AppDataArchiveRules.TRANSFER_STATE_DIRECTORY_NAME,
            ),
        )
        assertTrue(AppDataArchiveRules.shouldExportTopLevel("cache_nested"))
    }

    @Test
    fun `archive paths accept only normalized forward relative data paths`() {
        assertEquals(
            "shared_prefs/settings.xml",
            AppDataArchiveRules.dataRelativePath(
                "data/shared_prefs/settings.xml",
                isDirectory = false,
            ),
        )
        assertEquals(
            "empty",
            AppDataArchiveRules.dataRelativePath("data/empty/", isDirectory = true),
        )

        listOf(
            "../settings.xml",
            "/data/settings.xml",
            "data/../settings.xml",
            "data/./settings.xml",
            "data//settings.xml",
            "data\\settings.xml",
            "data/C:/settings.xml",
            "data/cache/settings.xml",
            "data/",
            "data/settings.xml/",
        ).forEach { unsafePath ->
            assertNull(
                unsafePath,
                AppDataArchiveRules.dataRelativePath(unsafePath, isDirectory = false),
            )
        }
    }

    @Test
    fun `archive paths bound full length segments and depth`() {
        assertNull(
            AppDataArchiveRules.dataRelativePath(
                "data/${"a".repeat(AppDataArchiveRules.MAX_RELATIVE_PATH_BYTES + 1)}",
                isDirectory = false,
            ),
        )
        assertNull(
            AppDataArchiveRules.dataRelativePath(
                "data/${"a".repeat(AppDataArchiveRules.MAX_PATH_SEGMENT_BYTES + 1)}",
                isDirectory = false,
            ),
        )
        assertNull(
            AppDataArchiveRules.dataRelativePath(
                "data/${List(AppDataArchiveRules.MAX_PATH_SEGMENTS + 1) { "a" }.joinToString("/")}",
                isDirectory = false,
            ),
        )
    }

    @Test
    fun `compatibility distinguishes app and webview versions`() {
        val current = environment()

        assertEquals(
            AppDataArchiveCompatibility.Same,
            AppDataArchiveRules.compatibility(current, manifest()),
        )
        assertEquals(
            AppDataArchiveCompatibility.AppMismatch,
            AppDataArchiveRules.compatibility(
                current,
                manifest().copy(appVersionCode = 43L, webViewVersion = "126.0"),
            ),
        )
        assertEquals(
            AppDataArchiveCompatibility.AppMismatch,
            AppDataArchiveRules.compatibility(
                current,
                manifest().copy(packageName = "dev.example.other"),
            ),
        )
        assertEquals(
            AppDataArchiveCompatibility.WebViewMismatch,
            AppDataArchiveRules.compatibility(
                current,
                manifest().copy(webViewVersion = "126.0"),
            ),
        )
        assertEquals(
            AppDataArchiveCompatibility.PlatformMismatch,
            AppDataArchiveRules.compatibility(
                current,
                manifest().copy(sdkInt = 34),
            ),
        )
    }

    @Test
    fun `archive size and count limits include exact boundaries`() {
        assertFalse(AppDataArchiveRules.isEntryLimitExceeded(100_000))
        assertTrue(AppDataArchiveRules.isEntryLimitExceeded(100_001))
        assertFalse(AppDataArchiveRules.isFileSizeExceeded(256L * 1024L * 1024L))
        assertTrue(AppDataArchiveRules.isFileSizeExceeded(256L * 1024L * 1024L + 1L))
        assertFalse(AppDataArchiveRules.isTotalSizeExceeded(2L * 1024L * 1024L * 1024L, 0L))
        assertTrue(AppDataArchiveRules.isTotalSizeExceeded(2L * 1024L * 1024L * 1024L, 1L))
    }

    private fun environment() = AppDataArchiveEnvironment(
        packageName = "dev.example.browser",
        appVersionName = "1.2.3",
        appVersionCode = 42L,
        webViewVersion = "125.0",
        sdkInt = 35,
    )

    private fun manifest() = AppDataArchiveManifest(
        packageName = "dev.example.browser",
        appVersionName = "1.2.3",
        appVersionCode = 42L,
        webViewVersion = "125.0",
        sdkInt = 35,
        exportedAtEpochMillis = 1_723_456_789_000L,
    )
}
