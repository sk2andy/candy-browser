package dev.sk2andy.materialbrowser.browser.annotation

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.BrowserEngineScrollListener
import dev.sk2andy.materialbrowser.browser.actions.BrowserContentTargetListener
import dev.sk2andy.materialbrowser.browser.gecko.AndroidBrowserEngineSessionPort
import dev.sk2andy.materialbrowser.browser.gecko.BrowserEnginePreviewCapture
import dev.sk2andy.materialbrowser.browser.gecko.GeckoFindResult
import dev.sk2andy.materialbrowser.browser.gecko.GeckoMediaCommand
import dev.sk2andy.materialbrowser.browser.gecko.GeckoMediaSessionStateListener
import dev.sk2andy.materialbrowser.browser.gecko.GeckoNavigationRequestListener
import dev.sk2andy.materialbrowser.browser.gecko.GeckoPrivacyPolicy
import dev.sk2andy.materialbrowser.data.WebAnnotationStore
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommand
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebAnnotationCoordinatorInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = WebAnnotationStore(context)
    private val session = FakeEngineSession("annotation-tab")
    private lateinit var coordinator: WebAnnotationCoordinator
    private val annotation = WebAnnotation(
        id = "quote", profileId = "work", url = "https://example.org/article", title = "Article",
        quote = "Original passage", note = "**Saved** note", createdAt = 100,
    )

    @Before
    fun setUp() {
        assertTrue(store.clear())
        assertTrue(store.save(annotation, isPrivate = false))
        composeRule.runOnIdle {
            coordinator = WebAnnotationCoordinator(context)
            coordinator.bind(session, "work", annotation.url, 1) { true }
        }
        composeRule.waitUntil(5_000) { coordinator.pageAnnotations == listOf(annotation) }
    }

    @After
    fun tearDown() {
        composeRule.runOnIdle { coordinator.close() }
        store.clear()
    }

    @Test
    fun pageTapReadsOnlyKnownCurrentPageAnnotationAndDetachRejectsOldCallback() {
        composeRule.runOnIdle {
            val click = requireNotNull(session.clickListener)
            click("unknown")
            assertNull(coordinator.reader)
            click(annotation.id)
            assertEquals(annotation, coordinator.reader)
            assertNull(coordinator.editor)
            coordinator.detach()
            assertNull(session.clickListener)
            click(annotation.id)
            assertNull(coordinator.reader)
        }
    }

    @Test
    fun listJumpClosesLibraryOnSuccessAndPreservesNoteWhenQuoteMissing() {
        composeRule.runOnIdle {
            coordinator.showPageAnnotations()
            coordinator.scrollTo(annotation)
            val (payload, complete) = session.operations.single()
            assertEquals("scroll-to-annotation", payload.getString("operation"))
            assertEquals(annotation.id, payload.getString("id"))
            assertEquals(annotation.quote, payload.getString("quote"))
            complete(true)
            assertFalse(coordinator.isLibraryVisible)
            assertNull(coordinator.reader)

            coordinator.showPageAnnotations()
            coordinator.scrollTo(annotation)
            session.operations.last().second(false)
            assertEquals(annotation, coordinator.reader)
            assertTrue(coordinator.scrollFailed)
            assertFalse(coordinator.isLibraryVisible)
        }
    }

    @Test
    fun delayedJumpCannotDismissNewEditorOrReopenAfterNavigation() {
        composeRule.runOnIdle {
            coordinator.showPageAnnotations()
            coordinator.scrollTo(annotation)
            coordinator.edit(annotation)
            session.operations.single().second(false)
            assertEquals(annotation, coordinator.editor)
            assertNull(coordinator.reader)
            coordinator.scrollTo(annotation)
            coordinator.detach()
            session.operations.last().second(false)
            assertNull(coordinator.reader)
            assertNull(coordinator.editor)
        }
    }

    @Test
    fun saveCompletesWithoutReopeningDismissedNote() {
        composeRule.runOnIdle {
            coordinator.edit(annotation)
            coordinator.save("Updated note")
            coordinator.dismissEditor()
        }
        composeRule.waitUntil(5_000) { coordinator.pageAnnotations.singleOrNull()?.note == "Updated note" }
        composeRule.runOnIdle {
            assertNull(coordinator.reader)
            assertNull(coordinator.editor)
        }
    }

    @Test
    fun saveExposesBusyStateAndRejectsDuplicateSaveOrDelete() {
        composeRule.runOnIdle {
            coordinator.edit(annotation)
            coordinator.save("First save")
            assertTrue(coordinator.isSaving)
            coordinator.save("Duplicate save")
            coordinator.delete(annotation)
            coordinator.read(annotation)
            coordinator.showPageAnnotations()
            assertEquals(annotation, coordinator.editor)
            assertTrue(coordinator.isSaving)
        }
        composeRule.waitUntil(5_000) {
            !coordinator.isSaving && coordinator.pageAnnotations.singleOrNull()?.note == "First save"
        }
        composeRule.runOnIdle {
            assertEquals("First save", coordinator.reader?.note)
            assertNull(coordinator.editor)
            assertFalse(coordinator.failed)
        }
    }

    @Test
    fun deleteExposesBusyStateAndRejectsOverlappingSave() {
        composeRule.runOnIdle {
            coordinator.edit(annotation)
            coordinator.delete(annotation)
            assertTrue(coordinator.isSaving)
            coordinator.save("Must not restore deleted annotation")
            coordinator.delete(annotation)
        }
        composeRule.waitUntil(5_000) { !coordinator.isSaving && coordinator.pageAnnotations.isEmpty() }
        composeRule.runOnIdle {
            assertNull(coordinator.editor)
            assertNull(coordinator.reader)
            assertTrue(store.all().isEmpty())
        }
    }

    @Test
    fun suspensionKeepsEditorAndSameBindingResumeRejectsOldListener() {
        composeRule.runOnIdle {
            coordinator.edit(annotation)
            val oldClick = requireNotNull(session.clickListener)
            coordinator.suspend()
            coordinator.bind(session, "work", annotation.url, 1, isActive = false) { true }
            assertEquals(annotation, coordinator.editor)
            assertFalse(coordinator.canOpenPageAnnotations)
            assertFalse(coordinator.isSaving)
            assertNull(session.clickListener)
            oldClick(annotation.id)
            coordinator.save("Must not save while suspended")
            assertNull(coordinator.reader)
            assertEquals(annotation, coordinator.editor)
            assertFalse(coordinator.isSaving)

            coordinator.bind(session, "work", annotation.url, 1) { true }
            assertTrue(coordinator.canOpenPageAnnotations)
            assertEquals(annotation, coordinator.editor)
            oldClick(annotation.id)
            assertEquals(annotation, coordinator.editor)
            assertNull(coordinator.reader)
            requireNotNull(session.clickListener)(annotation.id)
            assertEquals(annotation, coordinator.reader)
        }
    }

    @Test
    fun suspendedSaveCannotCloseResumedEditorWhenWriteFinishes() {
        composeRule.runOnIdle {
            coordinator.edit(annotation)
            coordinator.save("Saved before pause")
            assertTrue(coordinator.isSaving)
            coordinator.suspend()
            assertFalse(coordinator.isSaving)
            coordinator.bind(session, "work", annotation.url, 1) { true }
            assertEquals(annotation, coordinator.editor)
        }
        composeRule.waitUntil(5_000) { coordinator.pageAnnotations.singleOrNull()?.note == "Saved before pause" }
        composeRule.runOnIdle {
            assertEquals(annotation, coordinator.editor)
            assertNull(coordinator.reader)
            assertFalse(coordinator.isSaving)
        }
    }

    @Test
    fun changedSuspendedSessionProfilePageOrGenerationClearsEditor() {
        composeRule.runOnIdle {
            val otherSession = FakeEngineSession("other-tab")
            val rebindings = listOf<() -> Unit>(
                { coordinator.bind(otherSession, "work", annotation.url, 1, isActive = false) { true } },
                { coordinator.bind(session, "other-profile", annotation.url, 1, isActive = false) { true } },
                { coordinator.bind(session, "work", "https://example.org/other", 1, isActive = false) { true } },
                { coordinator.bind(session, "work", annotation.url, 2, isActive = false) { true } },
            )
            for (rebind in rebindings) {
                coordinator.bind(session, "work", annotation.url, 1) { true }
                coordinator.select("Unsaved quote", "Article")
                coordinator.suspend()
                assertTrue(coordinator.editor != null)
                rebind()
                assertNull(coordinator.editor)
                assertNull(coordinator.reader)
                assertFalse(coordinator.isSaving)
            }
        }
    }

    @Test
    fun suspensionWithoutEditorDropsLibraryAndReader() {
        composeRule.runOnIdle {
            coordinator.showPageAnnotations()
            coordinator.read(annotation)
            coordinator.suspend()
            assertFalse(coordinator.isLibraryVisible)
            assertNull(coordinator.reader)
            assertFalse(coordinator.canOpenPageAnnotations)
            assertNull(session.clickListener)
        }
    }

    private class FakeEngineSession(
        override val tabId: String,
    ) : AndroidBrowserEngineSessionPort {
        var clickListener: ((String) -> Unit)? = null
        val operations = mutableListOf<Pair<JSONObject, (Boolean) -> Unit>>()
        override fun execute(command: BrowserEngineCommand) = Unit
        override fun setAudioMuted(muted: Boolean) = Unit
        override fun setScrollListener(listener: BrowserEngineScrollListener?) = Unit
        override fun setWebAnnotationClickListener(listener: ((String) -> Unit)?) {
            clickListener = listener
        }
        override fun executeWebTextOperation(payload: String, onComplete: (Boolean) -> Unit) {
            val operation = JSONObject(payload)
            if (operation.getString("operation") == "highlights") onComplete(true)
            else operations += operation to onComplete
        }

        override fun createView(context: Context): View = View(context)
        override fun awaitContentPresented(listener: () -> Unit) = listener()
        override fun releaseView(view: View) = Unit
        override fun setActive(active: Boolean) = Unit
        override fun setMediaStateListener(listener: GeckoMediaSessionStateListener?) = Unit
        override fun setContentTargetListener(listener: BrowserContentTargetListener?) = Unit
        override fun setNavigationRequestListener(listener: GeckoNavigationRequestListener?) = Unit
        override fun setVideoAutoplayBlocked(blocked: Boolean) = Unit
        override fun executeMediaCommand(command: GeckoMediaCommand) = Unit
        override fun seekMedia(positionMillis: Long) = Unit
        override fun goToHistoryIndex(index: Int) = Unit
        override fun historyUrlAtOffset(offset: Int): String? = null
        override fun clearFindInPage() = Unit
        override fun printPage(): Boolean = false
        override fun setDesktopMode(enabled: Boolean) = Unit
        override fun extractPageForReader(onComplete: (String?) -> Unit) = onComplete(null)

        override fun capturePreview(
            targetWidthPx: Int,
            visibleViewHeightPx: Int,
            maximumTargetHeightPx: Int,
            onComplete: (Bitmap?) -> Unit,
        ): BrowserEnginePreviewCapture? = null

        override fun findInPage(
            query: String,
            forward: Boolean,
            onComplete: (GeckoFindResult?) -> Unit,
        ) = onComplete(null)

        override fun updatePrivacyPolicy(
            policy: GeckoPrivacyPolicy,
            reloadOnCookiePermissionChange: Boolean,
            onReady: () -> Unit,
        ) = onReady()
    }

}
