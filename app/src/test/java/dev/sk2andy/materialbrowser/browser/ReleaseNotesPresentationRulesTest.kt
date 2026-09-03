package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesPresentationRulesTest {
    @Test
    fun `unseen release is presented once after an app update`() {
        assertTrue(
            ReleaseNotesPresentationRules.shouldPresent(
                isNewLaunch = true,
                isLauncherLaunch = true,
                isAppUpdate = true,
                contentAvailable = true,
                currentVersionCode = 32_000L,
                lastPresentedVersionCode = null,
            ),
        )
        assertFalse(
            ReleaseNotesPresentationRules.shouldPresent(
                isNewLaunch = true,
                isLauncherLaunch = true,
                isAppUpdate = true,
                contentAvailable = true,
                currentVersionCode = 32_000L,
                lastPresentedVersionCode = 32_000L,
            ),
        )
    }

    @Test
    fun `fresh installs external launches and recreation stay quiet`() {
        val baseline = ReleaseNotesRequest()

        listOf(
            baseline.copy(isAppUpdate = false),
            baseline.copy(isLauncherLaunch = false),
            baseline.copy(isNewLaunch = false),
            baseline.copy(contentAvailable = false),
        ).forEach { request ->
            assertFalse(request.shouldPresent())
        }
    }

    @Test
    fun `older or invalid builds never replace newer presented notes`() {
        assertFalse(ReleaseNotesRequest(currentVersionCode = 0L).shouldPresent())
        assertFalse(
            ReleaseNotesRequest(
                currentVersionCode = 31_000L,
                lastPresentedVersionCode = 32_000L,
            ).shouldPresent(),
        )
    }
}

private data class ReleaseNotesRequest(
    val isNewLaunch: Boolean = true,
    val isLauncherLaunch: Boolean = true,
    val isAppUpdate: Boolean = true,
    val contentAvailable: Boolean = true,
    val currentVersionCode: Long = 32_000L,
    val lastPresentedVersionCode: Long? = null,
) {
    fun shouldPresent(): Boolean = ReleaseNotesPresentationRules.shouldPresent(
        isNewLaunch = isNewLaunch,
        isLauncherLaunch = isLauncherLaunch,
        isAppUpdate = isAppUpdate,
        contentAvailable = contentAvailable,
        currentVersionCode = currentVersionCode,
        lastPresentedVersionCode = lastPresentedVersionCode,
    )
}
