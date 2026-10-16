package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.os.SystemClock
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.constellations.ConstellationDocument
import dev.sk2andy.materialbrowser.constellations.ConstellationSnapshot
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.ConstellationRepository
import dev.sk2andy.materialbrowser.data.ConstellationStore
import dev.sk2andy.materialbrowser.data.HistoryEntry
import dev.sk2andy.materialbrowser.data.HistoryRecordingMode
import dev.sk2andy.materialbrowser.browser.guest.GuestBubbleRules
import dev.sk2andy.materialbrowser.browser.guest.GuestBubbleSessionStore
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Requires the pinned model fixtures staged by the device-test runner; never downloads a model. */
@RunWith(AndroidJUnit4::class)
class BrowserControllerConstellationsInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository by lazy { ConstellationRepository.get(context) }
    private var controller: BrowserController? = null
    private var host: FrameLayout? = null

    @Before
    fun setUp() {
        assertTrue(repository.reset())
        activityRule.scenario.onActivity { activity ->
            clearPreferences(activity)
            val store = BrowserSessionStore(activity)
            val nowMillis = System.currentTimeMillis()
            assertTrue(store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.SystemWebView))
            assertTrue(store.saveHistoryRecordingMode(HistoryRecordingMode.Enabled))
            store.saveProfilesEnabled(true)
            store.saveProfiles(
                profiles = listOf(
                    BrowserProfile(id = HOME_PROFILE, emoji = "🏠", selectedTabId = HOME_TAB),
                    BrowserProfile(id = WORK_PROFILE, emoji = "💼", selectedTabId = WORK_TAB),
                ),
                activeProfileId = HOME_PROFILE,
            )
            assertTrue(store.saveTabsImmediately(
                tabs = listOf(
                    BrowserTab(id = HOME_TAB, lastAccessedAt = nowMillis, url = BLANK_URL, profileId = HOME_PROFILE),
                    BrowserTab(id = WORK_TAB, lastAccessedAt = nowMillis, url = BLANK_URL, profileId = WORK_PROFILE),
                ),
                selectedTabId = HOME_TAB,
            ))
            assertTrue(store.flush())
        }
        repository.setEnabled(HOME_PROFILE, true)
        repository.setEnabled(WORK_PROFILE, true)
        await("Both profiles must opt in before navigation") {
            repository.isEnabled(HOME_PROFILE) && repository.isEnabled(WORK_PROFILE)
        }
        await("Stage the three pinned model fixtures before this test") { snapshot(HOME_PROFILE).modelReady }
        await("Previous model work must finish before test navigation") { !snapshot(HOME_PROFILE).busy }
    }

    @After
    fun tearDown() {
        activityRule.scenario.onActivity { activity ->
            host?.let { view -> controller?.detachBrowserEngineView(view) }
            controller?.destroy()
            controller = null
            host = null
            clearPreferences(activity)
        }
        assertTrue(repository.reset())
    }

    @Test
    fun realNavigationEmbedsSameUrlInsideEachProfileAndIgnoresPrivateVisits() {
        val html = AtomicReference(page("Home privacy map", "Home privacy evidence", PRIVACY_TEXT))
        EdgeToEdgeSiteFixtureServer { html.get() }.use { server ->
            val sharedUrl = server.fixtureUrl("/site-matrix/constellations-shared")
            val privateUrl = server.fixtureUrl("/site-matrix/constellations-private")
            val openedProfile = AtomicReference<String>()
            activityRule.scenario.onActivity { activity ->
                val browser = BrowserController(
                    activity = activity,
                    onOpenConstellations = openedProfile::set,
                ).also { controller = it }
                browser.onStart()
                browser.onResume()
                val view = FrameLayout(activity).also { host = it }
                activity.addContentView(view, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ))
                browser.attachSelectedBrowserEngineView(view)
                assertTrue(browser.canOpenConstellations)
                browser.openConstellations()
                assertEquals(HOME_PROFILE, openedProfile.get())
                assertFalse(browser.openConstellationPage(sharedUrl, WORK_PROFILE))
                browser.submitAddress(sharedUrl)
            }

            val homeDocument = awaitDocument(HOME_PROFILE, sharedUrl)
            assertEquals("Home privacy map", homeDocument.title)
            assertTrue(homeDocument.excerpt.contains("Home privacy evidence"))
            assertEquals(512, homeDocument.embedding.size)
            assertTrue(snapshot(WORK_PROFILE).documents.isEmpty())

            html.set(page("Work privacy map", "Work privacy evidence", PRIVACY_TEXT))
            activityRule.scenario.onActivity {
                val browser = requireNotNull(controller)
                browser.detachBrowserEngineView(requireNotNull(host))
                assertTrue(browser.selectProfile(WORK_PROFILE))
                browser.attachSelectedBrowserEngineView(requireNotNull(host))
                browser.submitAddress(sharedUrl)
            }
            val workDocument = awaitDocument(WORK_PROFILE, sharedUrl)
            assertEquals("Work privacy map", workDocument.title)
            assertTrue(workDocument.excerpt.contains("Work privacy evidence"))
            assertEquals(HOME_PROFILE, snapshot(HOME_PROFILE).documents.single().profileId)
            assertEquals("Home privacy map", snapshot(HOME_PROFILE).documents.single().title)
            assertEquals(WORK_PROFILE, workDocument.profileId)

            html.set(page("Private privacy map", "Private evidence never persists", PRIVACY_TEXT))
            activityRule.scenario.onActivity {
                val browser = requireNotNull(controller)
                browser.detachBrowserEngineView(requireNotNull(host))
                browser.createTab(isIncognito = true)
                browser.attachSelectedBrowserEngineView(requireNotNull(host))
                assertFalse(browser.canOpenConstellations)
                assertFalse(browser.openConstellationPage(sharedUrl, WORK_PROFILE))
                openedProfile.set(null)
                browser.openConstellations()
                assertEquals(null, openedProfile.get())
                browser.submitAddress(privateUrl)
            }
            await("Private fixture must finish loading before checking persistence") {
                var complete = false
                activityRule.scenario.onActivity {
                    val tab = requireNotNull(controller).selectedTabForTesting()
                    complete = tab.title == "Private privacy map" && !tab.isLoading
                }
                complete
            }
            assertEquals(listOf(sharedUrl), snapshot(HOME_PROFILE).documents.map { it.url })
            assertEquals(listOf(sharedUrl), snapshot(WORK_PROFILE).documents.map { it.url })
            assertFalse(BrowserSessionStore(context).loadHistory().any { it.url == privateUrl })
        }
    }

    @Test
    fun cleanupEpochAndPrivateBoundaryRejectLateExtractedContent() {
        val url = "https://example.com/late-constellation"
        val visitedAt = System.currentTimeMillis()
        assertTrue(BrowserSessionStore(context).commitHistory(listOf(
            HistoryEntry(url, "Late content", visitedAt, HOME_PROFILE),
        )))
        val extracted = JSONObject().put("sourceUrl", url).put("title", "Late content")
            .put("text", PRIVACY_TEXT).toString()
        val oldEpoch = repository.captureCleanupEpoch()
        assertTrue(repository.clear())
        repository.indexExtracted(
            rawJson = extracted,
            profileId = HOME_PROFILE,
            expectedUrl = url,
            visitedAt = visitedAt,
            expectedCleanupEpoch = oldEpoch,
            isPrivate = false,
        )
        repository.indexExtracted(
            rawJson = extracted,
            profileId = HOME_PROFILE,
            expectedUrl = url,
            visitedAt = visitedAt,
            expectedCleanupEpoch = repository.captureCleanupEpoch(),
            isPrivate = true,
        )
        await("Rejected callbacks must leave no model work pending") { !snapshot(HOME_PROFILE).busy }
        val result = snapshot(HOME_PROFILE)
        assertTrue(result.enabled)
        assertTrue(result.documents.isEmpty())
        assertFalse(result.busy)
    }

    @Test
    fun guestOwnerLockHidesExistingMapAndPreventsOpeningIt() {
        val document = ConstellationDocument(
            profileId = HOME_PROFILE,
            url = "https://example.com/owner-map",
            title = "Owner-only map",
            excerpt = "Private owner context",
            visitedAt = 1L,
            embedding = List(512) { if (it == 0) 1f else 0f },
        )
        ConstellationStore(context).upsert(document)
        assertEquals(listOf(document), snapshot(HOME_PROFILE).documents)
        activityRule.scenario.onActivity { activity ->
            controller = BrowserController(activity)
            assertTrue(requireNotNull(controller).canOpenConstellations)
        }
        val directory = File(context.noBackupFilesDir, GuestBubbleRules.SESSION_DIRECTORY_NAME)
        assertTrue(directory.isDirectory || directory.mkdirs())
        val marker = File(directory, "session")
        assertFalse(marker.exists())
        try {
            // A malformed durable marker must also fail closed, matching Guest Bubble recovery.
            marker.writeText("interrupted guest marker")
            assertTrue(GuestBubbleSessionStore(context).isLocked())
            val hidden = snapshot(HOME_PROFILE)
            assertFalse(hidden.enabled)
            assertTrue(hidden.documents.isEmpty())
            activityRule.scenario.onActivity {
                assertFalse(requireNotNull(controller).canOpenConstellations)
                assertFalse(requireNotNull(controller).openConstellationPage(document.url, HOME_PROFILE))
            }
        } finally {
            assertTrue(marker.delete())
        }
        assertEquals(listOf(document), snapshot(HOME_PROFILE).documents)
    }

    private fun awaitDocument(profileId: String, url: String): ConstellationDocument {
        var document: ConstellationDocument? = null
        try {
            await("Expected real extracted embedding for $profileId") {
                document = snapshot(profileId).documents.firstOrNull { it.url == url }
                document != null
            }
        } catch (failure: AssertionError) {
            val state = snapshot(profileId)
            val history = BrowserSessionStore(context).loadHistory().map {
                "${it.profileId}:${it.url}:${it.lastVisitedAt}"
            }
            var tabState = ""
            activityRule.scenario.onActivity {
                val browser = requireNotNull(controller)
                val tab = browser.selectedTabForTesting()
                tabState = "${tab.profileId}:${tab.url}:${tab.isLoading}:${tab.title}"
            }
            throw AssertionError(
                "${failure.message}; enabled=${state.enabled}, busy=${state.busy}, " +
                    "error=${state.error}, tab=$tabState, history=$history",
                failure,
            )
        }
        return requireNotNull(document)
    }

    private fun snapshot(profileId: String): ConstellationSnapshot {
        val completed = CountDownLatch(1)
        val result = AtomicReference<ConstellationSnapshot>()
        repository.snapshot(profileId) { value -> result.set(value); completed.countDown() }
        assertTrue("Repository snapshot callback timed out", completed.await(10, TimeUnit.SECONDS))
        return requireNotNull(result.get())
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 90_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(50L)
        }
        assertTrue(message, condition())
    }

    private fun clearPreferences(context: Context) {
        assertTrue(context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit())
    }

    private fun page(title: String, marker: String, text: String): String = """
        <!doctype html>
        <html><head><meta name="viewport" content="width=device-width, initial-scale=1"><title>$title</title></head>
        <body><main><h1>$marker</h1><p>$text</p></main></body></html>
    """.trimIndent()

    private companion object {
        const val HOME_PROFILE = "constellation-home"
        const val WORK_PROFILE = "constellation-work"
        const val HOME_TAB = "constellation-home-tab"
        const val WORK_TAB = "constellation-work-tab"
        const val PRIVACY_TEXT = "Datenschutz schützt persönliche Daten im Browser. " +
            "Lokale Verarbeitung bewahrt vertrauliche Informationen auf dem eigenen Gerät. " +
            "Private browsing should keep sensitive information out of persistent history."
    }
}
