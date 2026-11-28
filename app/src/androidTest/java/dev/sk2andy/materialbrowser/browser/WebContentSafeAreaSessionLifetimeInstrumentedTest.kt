package dev.sk2andy.materialbrowser.browser

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.CandyApplication
import dev.sk2andy.materialbrowser.MainActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebContentSafeAreaSessionLifetimeInstrumentedTest {
    @Test
    fun rememberedDomainSurvivesActivityRecreation() {
        val app = ApplicationProvider.getApplicationContext<CandyApplication>()
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "pm grant ${app.packageName} android.permission.POST_NOTIFICATIONS",
        ).close()
        val tab = BrowserTab(id = "safe-area-lifetime", lastAccessedAt = 0)
        val url = "https://safe-area-lifetime.example/first"
        app.webContentSafeAreaSession.clear()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                app.webContentSafeAreaSession.remember(tab, url)
                lateinit var privateTab: BrowserTab
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    controller.createTab(isIncognito = true)
                    privateTab = controller.selectedTab
                    assertTrue(privateTab.isIncognito)
                    app.webContentSafeAreaSession.remember(privateTab, url)
                }
                scenario.recreate()
                assertTrue(app.webContentSafeAreaSession.contains(tab, "https://safe-area-lifetime.example/next"))
                assertFalse(app.webContentSafeAreaSession.contains(privateTab, url))
            }
        } finally {
            app.webContentSafeAreaSession.clear()
        }
    }
}
