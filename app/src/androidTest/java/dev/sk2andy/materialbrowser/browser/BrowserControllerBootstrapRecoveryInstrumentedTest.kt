package dev.sk2andy.materialbrowser.browser

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.CandyTrailRepository
import dev.sk2andy.materialbrowser.data.CandyTrailStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class BrowserControllerBootstrapRecoveryInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun persistedPrivacyBootstrapAddressRecoversCurrentTrailPage() {
        val tabId = "00000000-0000-0000-0000-000000000205"
        val pageUrl = "https://example.com/recovered"
        val store = BrowserSessionStore(composeRule.activity)
        val trailStore = CandyTrailStore(composeRule.activity)
        val (originalTabs, originalSelection) = store.loadTabs()
        val (_, activeProfileId) = store.loadProfiles()
        var controller: BrowserController? = null

        try {
            assertTrue(
                trailStore.save(
                    tabId,
                    CandyTrail(
                        tabId = tabId,
                        nodes = listOf(
                            CandyTrailNode(
                                id = "n0",
                                parentId = null,
                                url = pageUrl,
                                title = "Recovered page",
                                visitedAt = 1L,
                            ),
                        ),
                        currentNodeId = "n0",
                        nextOrdinal = 1L,
                    ),
                ),
            )
            composeRule.runOnIdle {
                assertTrue(
                    store.saveTabsImmediately(
                        tabs = listOf(
                            BrowserTab(
                                id = tabId,
                                lastAccessedAt = System.currentTimeMillis(),
                                profileId = activeProfileId,
                                url = STALE_BOOTSTRAP_URL,
                            ),
                        ),
                        selectedTabId = tabId,
                    ),
                )
                controller = BrowserController(composeRule.activity)
            }

            composeRule.waitUntil(timeoutMillis = 10_000) {
                controller?.selectedTab?.url == pageUrl
            }
            composeRule.runOnIdle {
                assertEquals(pageUrl, controller?.selectedTab?.url)
                assertEquals(pageUrl, store.loadTabs().first.single().url)
            }
        } finally {
            composeRule.runOnIdle {
                controller?.destroy()
                store.saveTabsImmediately(originalTabs, originalSelection.orEmpty())
            }
            CandyTrailRepository.get(composeRule.activity).flush()
            trailStore.delete(tabId)
        }
    }

    private companion object {
        const val STALE_BOOTSTRAP_URL =
            "moz-extension://5e6344e7-68a0-4a80-863c-0123456789ab/" +
                "bootstrap.html?token=11111111-2222-4333-8444-555555555555"
    }
}
