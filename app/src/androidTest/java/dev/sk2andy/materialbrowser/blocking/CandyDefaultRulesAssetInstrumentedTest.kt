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
        assertEquals(
            emptySet<CandyRuleKind>(),
            bundled.rules.map(CandyRule::kind).filterNot { it == CandyRuleKind.CosmeticCss }.toSet(),
        )
        assertEquals(
            setOf(
                "iframe[src^=\"https://plus.web.de/\"]",
                "iframe[src^=\"https://plus.gmx.net/\"]",
            ),
            bundled.cookieCosmeticRules.mapNotNull(CandyRule::cosmeticSelector)
                .filter { it.startsWith("iframe[src^=") }
                .toSet(),
        )
    }

    @Test
    fun networkHostAssetBuildsAllocationLightSortedIndex() {
        val bytes = InstrumentationRegistry.getInstrumentation().targetContext.assets
            .open("easylist_blocked_hosts.txt")
            .use { it.readBytes() }

        val index = SortedHostIndex.from(bytes)

        assertEquals(55_004, index.size)
        assertEquals(true, "0.0.0.1" in index)
        assertEquals(true, "zzzmjfixezere.site" in index)
        assertEquals(false, "example.com" in index)
    }

    private companion object {
        const val RULE_COUNT_PREFIX = "# Rule count:"
    }
}
