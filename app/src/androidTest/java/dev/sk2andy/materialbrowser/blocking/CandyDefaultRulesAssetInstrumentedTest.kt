package dev.sk2andy.materialbrowser.blocking

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CandyDefaultRulesAssetInstrumentedTest {
    @Test
    fun bundledAssetIsStrictlyValidAndMatchesDeclaredCount() {
        val text = InstrumentationRegistry.getInstrumentation().targetContext.assets
            .open("candy_default_rules.txt")
            .bufferedReader()
            .use { it.readText() }
        val declaredCount = text.lineSequence()
            .first { it.startsWith(RULE_COUNT_PREFIX) }
            .removePrefix(RULE_COUNT_PREFIX)
            .trim()
            .toInt()

        val bundled = BundledCandyRules.parse(text)

        assertEquals(declaredCount, bundled.rules.size)
    }

    @Test
    fun bundledWebViewAssetsAreStrictlyValid() {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        val consentActions = assets.open("candy_consent_actions.txt")
            .bufferedReader()
            .use { BundledConsentActions.parse(it.readText()) }
        val requestRules = assets.open("candy_request_rules.txt")
            .bufferedReader()
            .use { BundledRequestRules.parse(it.readText()) }

        assertEquals(4, consentActions.size)
        assertEquals(0, requestRules.rules.size)
    }

    private companion object {
        const val RULE_COUNT_PREFIX = "# Rule count:"
    }
}
