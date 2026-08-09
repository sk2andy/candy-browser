package dev.sk2andy.materialbrowser.capsule

import android.app.Activity
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.SiteCapsuleEditorActivity
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SiteCapsuleEditorContractInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val contract = SiteCapsuleEditorContract()

    @Test
    fun addRequestUsesExplicitActivityAndRoundTripsBoundedPreview() {
        val preview = Bitmap.createBitmap(256, 128, Bitmap.Config.ARGB_8888)
        val request = SiteCapsuleEditorRequest(
            existing = null,
            sourceTabId = "tab-id",
            sourceTitle = "Example",
            sourceUrl = "https://example.com",
            profiles = listOf(BrowserProfile("candy", "🍬")),
            activeProfileId = "candy",
            profileIsolationSupported = true,
            pinningSupported = true,
            canCreate = true,
            canCreateDedicatedProfile = true,
            previewIcon = preview,
        )

        val intent = contract.createIntent(context, request)
        val decoded = requireNotNull(SiteCapsuleEditorContract.requestFrom(intent))

        assertEquals(SiteCapsuleEditorActivity::class.java.name, intent.component?.className)
        assertNotNull(decoded)
        assertEquals(request.sourceTabId, decoded.sourceTabId)
        assertEquals(request.sourceTitle, decoded.sourceTitle)
        assertEquals(request.sourceUrl, decoded.sourceUrl)
        assertEquals(request.profiles.map(BrowserProfile::id), decoded.profiles.map(BrowserProfile::id))
        assertTrue(decoded.canCreateDedicatedProfile)
        assertTrue(requireNotNull(decoded.previewIcon).width <= 96)
        assertTrue(requireNotNull(decoded.previewIcon).height <= 96)
        preview.recycle()
        decoded.previewIcon?.recycle()
    }

    @Test
    fun submissionRoundTripsAndCanceledResultReturnsNull() {
        val sourceFavicon = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        val submission = SiteCapsuleEditorSubmission(
            existingId = "04a74ad8-7533-460c-bfbf-a135968940d5",
            sourceTabId = "tab-id",
            name = "Mail",
            startUrl = "https://mail.example",
            selectedProfileId = "candy",
            createDedicatedProfile = true,
            dedicatedEmoji = "📬",
            isolatedStorageRequested = true,
            navigationMode = CapsuleNavigationMode.SameRegistrableDomain,
            chromeMode = CapsuleChromeMode.Minimal,
            iconMode = CapsuleIconMode.ProfileFallback,
            sourceFavicon = sourceFavicon,
        )
        val resultIntent = SiteCapsuleEditorContract.resultIntent(submission)
        val decoded = requireNotNull(contract.parseResult(Activity.RESULT_OK, resultIntent))

        assertEquals(submission.copy(sourceFavicon = null), decoded.copy(sourceFavicon = null))
        assertEquals(48, decoded.sourceFavicon?.width)
        assertEquals(48, decoded.sourceFavicon?.height)
        assertNull(contract.parseResult(Activity.RESULT_CANCELED, resultIntent))
        sourceFavicon.recycle()
        decoded.sourceFavicon?.recycle()
    }
}
