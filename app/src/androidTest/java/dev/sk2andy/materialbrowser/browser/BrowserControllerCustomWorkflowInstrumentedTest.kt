package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.os.SystemClock
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.actions.WebContentTarget
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowBinding
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowPlacement
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflow
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowConfiguration
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowFailure
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowStep
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.CustomWorkflowStore
import dev.sk2andy.materialbrowser.reader.ReaderLibraryStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserControllerCustomWorkflowInstrumentedTest {
    @get:Rule val activityRule = ActivityScenarioRule(ComponentActivity::class.java)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var controller: BrowserController? = null
    private var host: FrameLayout? = null
    private val pin = CustomWorkflow("pin-link", "Pin link", steps = listOf(CustomWorkflowStep.PinTab))
    private val save = CustomWorkflow("save-link", "Save link", steps = listOf(CustomWorkflowStep.SaveReaderOffline, CustomWorkflowStep.CloseTab))

    @Before fun setUp() {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        BrowserSessionStore(context).saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.SystemWebView)
        ReaderLibraryStore(context).clear()
        assertTrue(CustomWorkflowStore(context).save(CustomWorkflowConfiguration(listOf(pin, save))))
    }

    @After fun tearDown() {
        activityRule.scenario.onActivity {
            host?.let { controller?.detachBrowserEngineView(it) }
            controller?.destroy()
        }
        ReaderLibraryStore(context).clear()
        CustomWorkflowStore(context).save(CustomWorkflowConfiguration())
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun linkPinUsesTargetAndDeletedDefinitionDoesNothing() {
        EdgeToEdgeSiteFixtureServer { article() }.use { server ->
            open(server.fixtureUrl("/source"))
            activityRule.scenario.onActivity {
                val browser = requireNotNull(controller)
                val source = browser.selectedTab.id
                browser.runCustomWorkflow(pin.id, inputUrl = server.fixtureUrl("/target"))
                assertNull(browser.customWorkflowRunState?.failure)
                assertEquals(source, browser.selectedTab.id)
                assertTrue(browser.tabs.any { it.url == server.fixtureUrl("/target") && it.isPinned })
                val count = browser.tabs.size
                browser.runCustomWorkflow("deleted", inputUrl = server.fixtureUrl("/missing"))
                assertEquals(count, browser.tabs.size)
            }
        }
    }

    @Test fun hiddenLinkExtractionSavesTargetAndKeepsContainingTab() {
        EdgeToEdgeSiteFixtureServer { article() }.use { server ->
            open(server.fixtureUrl("/source"))
            var source = ""
            activityRule.scenario.onActivity {
                source = requireNotNull(controller).selectedTab.id
                requireNotNull(controller).runCustomWorkflow(save.id, inputUrl = server.fixtureUrl("/target"))
            }
            await { controller?.customWorkflowRunState?.running == false }
            activityRule.scenario.onActivity {
                assertNull(controller?.customWorkflowRunState?.failure)
                assertEquals(source, controller?.selectedTab?.id)
                assertEquals(0, controller?.activeLinkPeekPreviewCountForTesting)
            }
            assertEquals(server.fixtureUrl("/target"), ReaderLibraryStore(context).load(false).snapshots.single().document.sourceUrl)
        }
    }

    @Test fun geckoHiddenLinkExtractionSavesTargetAndKeepsContainingTab() {
        BrowserSessionStore(context).saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView)
        hiddenLinkExtractionSavesTargetAndKeepsContainingTab()
    }

    @Test fun selectedArticleClosesOnlyAfterPersistedSave() {
        EdgeToEdgeSiteFixtureServer { article() }.use { server ->
            val url = server.fixtureUrl("/selected")
            open(url)
            var source = ""
            activityRule.scenario.onActivity {
                source = requireNotNull(controller).selectedTab.id
                requireNotNull(controller).runCustomWorkflow(save.id)
            }
            await { controller?.customWorkflowRunState?.running == false }
            activityRule.scenario.onActivity {
                assertNull(controller?.customWorkflowRunState?.failure)
                assertTrue(requireNotNull(controller).tabs.none { it.id == source })
            }
            assertEquals(url, ReaderLibraryStore(context).load(false).snapshots.single().document.sourceUrl)
        }
    }

    @Test fun longPressUsesBoundWorkflowAndDeletedBindingNeverFallsBack() {
        BrowserSessionStore(context).saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView)
        val configuration = CustomWorkflowConfiguration(
            listOf(pin, save), listOf(CustomWorkflowBinding(CustomWorkflowPlacement.LinkLongPress, 0, pin.id)),
        )
        assertTrue(CustomWorkflowStore(context).save(configuration))
        EdgeToEdgeSiteFixtureServer { article() }.use { server ->
            val target = server.fixtureUrl("/target")
            open(server.fixtureUrl("/source"))
            activityRule.scenario.onActivity {
                val browser = requireNotNull(controller)
                browser.dispatchSelectedGeckoContentTargetForTesting(WebContentTarget(linkUrl = target))
            }
            await { controller?.tabs?.any { it.url == target && it.isPinned } == true }
            activityRule.scenario.onActivity {
                requireNotNull(controller).updateCustomWorkflowConfiguration(
                    configuration.copy(workflows = listOf(save)),
                ) { assertTrue(it) }
            }
            await { controller?.customWorkflowConfiguration?.workflows?.none { it.id == pin.id } == true }
            activityRule.scenario.onActivity {
                val browser = requireNotNull(controller)
                val before = browser.tabs.map { it.id }
                browser.dispatchSelectedGeckoContentTargetForTesting(WebContentTarget(linkUrl = server.fixtureUrl("/deleted")))
                assertEquals(before, browser.tabs.map { it.id })
                assertFalse(browser.contentActions.isVisible)
            }
        }
    }

    @Test fun privateSaveIsRejectedBeforeAnyPersistentEffect() {
        EdgeToEdgeSiteFixtureServer { article() }.use { server ->
            open(server.fixtureUrl("/private"), isPrivate = true)
            activityRule.scenario.onActivity {
                val browser = requireNotNull(controller)
                val source = browser.selectedTab.id
                browser.runCustomWorkflow(save.id)
                assertEquals(CustomWorkflowFailure.PrivatePersistence, browser.customWorkflowRunState?.failure)
                assertEquals(source, browser.selectedTab.id)
            }
            assertTrue(ReaderLibraryStore(context).load(false).snapshots.isEmpty())
        }
    }

    private fun open(url: String, isPrivate: Boolean = false) {
        activityRule.scenario.onActivity { activity ->
            val browser = BrowserController(activity).also { controller = it }
            browser.onStart()
            browser.onResume()
            if (isPrivate) browser.createTab(isIncognito = true)
            val view = FrameLayout(activity).also { host = it }
            activity.addContentView(view, FrameLayout.LayoutParams(-1, -1))
            browser.attachSelectedBrowserEngineView(view)
            browser.submitAddress(url)
        }
        await {
            controller?.selectedTab?.let { it.url == url && !it.isLoading && it.title == "Workflow article" } == true &&
                controller?.customWorkflowConfiguration?.workflows?.size == 2
        }
    }

    private fun await(check: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 35_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var done = false
            activityRule.scenario.onActivity { done = check() }
            if (done) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Workflow condition did not complete")
    }

    private fun article(): String = "<html><head><title>Workflow article</title></head><body><article><h1>Workflow article</h1>" +
        (1..12).joinToString("") { "<p>This is an offline article with enough meaningful text to exercise reader extraction, preserve the requested URL, and verify that the containing browser tab remains available after saving the linked page.</p>" } +
        "</article></body></html>"
}
