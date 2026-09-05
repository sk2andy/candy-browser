package dev.sk2andy.materialbrowser.browser

import android.app.Activity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.ProfileWallpaperEditorActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileWallpaperEditorContractInstrumentedTest {
    private val contract = ProfileWallpaperEditorContract()

    @Test
    fun requestRoundTripsProfileAndBoundedTransformWithoutBitmapPayload() {
        val request = ProfileWallpaperEditorRequest(
            profileId = "legacy-work",
            target = ProfileWallpaperTarget.TabSwitcher,
            wallpaper = ProfileWallpaper(
                zoom = 2.5f,
                normalizedPanX = -0.25f,
                normalizedPanY = 0.75f,
            ),
        )

        val intent = contract.createIntent(ApplicationProvider.getApplicationContext(), request)
        val restored = requireNotNull(ProfileWallpaperEditorContract.requestFrom(intent))

        assertEquals(ProfileWallpaperEditorActivity::class.java.name, intent.component?.className)
        assertEquals(request, restored)
        assertFalse(intent.hasExtra("bitmap"))
    }

    @Test
    fun resultRoundTripsRemovalAndBoundsInvalidTransform() {
        val removedIntent = ProfileWallpaperEditorContract.resultIntent(
            ProfileWallpaperEditorSubmission(
                "candy",
                ProfileWallpaperTarget.NewTab,
                wallpaper = null,
            ),
        )
        assertEquals(
            ProfileWallpaperEditorSubmission(
                "candy",
                ProfileWallpaperTarget.NewTab,
                wallpaper = null,
            ),
            contract.parseResult(Activity.RESULT_OK, removedIntent),
        )

        val invalidTransformIntent = ProfileWallpaperEditorContract.resultIntent(
            ProfileWallpaperEditorSubmission(
                "candy",
                ProfileWallpaperTarget.TabSwitcher,
                ProfileWallpaper(Float.POSITIVE_INFINITY, -9f, 9f),
            ),
        )
        assertEquals(
            ProfileWallpaperEditorSubmission(
                "candy",
                ProfileWallpaperTarget.TabSwitcher,
                ProfileWallpaper(1f, -1f, 1f),
            ),
            contract.parseResult(Activity.RESULT_OK, invalidTransformIntent),
        )
    }

    @Test
    fun canceledOrInvalidResultIsRejected() {
        val invalidRequest = contract.createIntent(
            ApplicationProvider.getApplicationContext(),
            ProfileWallpaperEditorRequest(
                "\n",
                ProfileWallpaperTarget.NewTab,
                wallpaper = null,
            ),
        )
        val unknownTargetRequest = contract.createIntent(
            ApplicationProvider.getApplicationContext(),
            ProfileWallpaperEditorRequest(
                "candy",
                ProfileWallpaperTarget.NewTab,
                wallpaper = null,
            ),
        ).putExtra("profile_wallpaper.target", "future_target")

        assertNull(ProfileWallpaperEditorContract.requestFrom(invalidRequest))
        assertNull(ProfileWallpaperEditorContract.requestFrom(unknownTargetRequest))
        assertNull(contract.parseResult(Activity.RESULT_CANCELED, null))
    }
}
