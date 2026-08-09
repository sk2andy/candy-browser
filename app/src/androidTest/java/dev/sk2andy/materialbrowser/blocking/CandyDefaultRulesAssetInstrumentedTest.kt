package dev.sk2andy.materialbrowser.blocking

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun bundledAssetCoversModernGoogleAndRedditAds() {
        val text = InstrumentationRegistry.getInstrumentation().targetContext.assets
            .open("easylist_cosmetic_rules.txt")
            .bufferedReader()
            .use { it.readText() }
        val bundled = EasyListCosmeticRules.parse(text)

        assertEquals(17_149, bundled.size)
        assertEquals(16_497, bundled.hidingRules.size)
        assertEquals(652, bundled.exceptionRules.size)
        assertEquals(34, bundled.hidingRules.count { it.hostPattern == "www.google.*" })
        assertTrue(bundled.exceptionRules.any {
            it.hostPattern == "ads.google.com" && it.selector == ".video-ads"
        })
        val google = bundled.selectors("https://www.google.com/search?q=hotel")
        assertTrue(google.toString(), "#tads[aria-label]" in google)
        assertTrue(google.toString(), "#google-s-ad" in google)
        assertTrue(google.toString(), "div[data-is-ad=\"1\"]" in google)
        assertTrue(bundled.selectors("https://www.google.de/search?q=hotel").isNotEmpty())
        assertTrue(bundled.selectors("https://www.google.fr/search?q=hotel").isNotEmpty())
        assertTrue(bundled.selectors("https://www.google.co.kr/search?q=hotel").isNotEmpty())
        assertTrue(bundled.selectors("https://www.google.com.sg/search?q=hotel").isNotEmpty())
        assertTrue(bundled.selectors("https://www.google.evil.com/search?q=hotel").isEmpty())
        assertTrue(bundled.selectors("https://www.google.com.de/search?q=hotel").isEmpty())
        assertTrue(bundled.selectors("https://mail.google.com/").isEmpty())
        assertTrue(bundled.selectors("https://maps.google.com/").isEmpty())
        assertTrue(bundled.selectors("https://accounts.google.com/").isEmpty())

        val reddit = bundled.selectors("https://www.reddit.com/r/popular/")
        assertTrue(reddit.toString(), "shreddit-ad-post" in reddit)
        assertTrue(reddit.toString(), "div[data-before-content=\"advertisement\"]" in reddit)
        assertTrue(reddit.toString(), reddit.any {
            it.startsWith("[data-faceplate-tracking-context*=")
        })

        val blocker = ContentBlocker(InstrumentationRegistry.getInstrumentation().targetContext)
        blocker.awaitCosmeticRulesForTesting()
        val googleScript = blocker.adCosmeticDocumentStartScript(
            "https://www.google.fr/search?q=hotel",
        )
        assertTrue("script length=${googleScript.length}", googleScript.length in 1..64_000)
        assertTrue(blocker.adCosmeticDocumentStartScript("https://mail.google.com/").isEmpty())

        val amazon = bundled.selectors("https://www.amazon.de/s?k=laptop")
        assertTrue(amazon.toString(), ".s-result-item:has(.puis-sponsored-label-text)" in amazon)
        assertTrue(
            amazon.toString(),
            "div[cel_widget_id^=\"MAIN-FEATURED_ASINS_LIST-\"]" in amazon,
        )
        assertTrue(bundled.selectors("https://www.amazon.co.jp/s?k=laptop").isNotEmpty())
        assertTrue(bundled.selectors("https://amazon.evil.com/s?k=laptop").isEmpty())
        assertTrue(bundled.selectors("https://amazon.com.de/s?k=laptop").isEmpty())

        val maximumHost = text.declaredValue("# Maximum resolved host:")
        val declaredMaximum = text.declaredValue("# Maximum resolved hide selectors:").toInt()
        val maximumSelectors = bundled.selectors("https://$maximumHost/")
        val worstScript = CandyCosmeticScript.create(maximumSelectors)
        assertTrue(
            "host=$maximumHost, selectors=${maximumSelectors.size}, declared=$declaredMaximum",
            maximumSelectors.size <= declaredMaximum && declaredMaximum <= 256,
        )
        assertTrue(
            "host=$maximumHost, script length=${worstScript.length}",
            worstScript.length <= 128_000,
        )
    }

    @Test
    fun bundledUassetsCosmeticsAreStrictAndMergeWithEasyListExceptions() {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        val easyList = EasyListCosmeticRules.parse(
            assets.open("easylist_cosmetic_rules.txt").bufferedReader().use { it.readText() },
        )
        val uAssets = EasyListCosmeticRules.parse(
            assets.open("uassets_cosmetic_rules.txt").bufferedReader().use { it.readText() },
            EasyListCosmeticRules.UASSETS_HEADER,
        )
        val merged = EasyListCosmeticRules.merge(easyList, uAssets)

        assertEquals(2_002, uAssets.hidingRules.size)
        assertEquals(50, uAssets.exceptionRules.size)
        assertEquals(2_052, uAssets.size)
        assertEquals(18_487, merged.hidingRules.size)
        assertEquals(702, merged.exceptionRules.size)
        assertEquals(19_189, merged.size)

        assertTrue(".ad-wrapper" in merged.selectors("https://www.bild.de/"))
        assertTrue(".Bloque-anuncios" in merged.selectors("https://www.elmundo.es/"))
        assertTrue("#T-Shopping" in merged.selectors("https://www.t-online.de/"))
        assertTrue(
            "div[class][data-before-content=\"Werbung\"]:not([id])" in
                merged.selectors("https://www.reddit.com/r/popular/"),
        )
        assertTrue(
            easyList.selectors("https://fuqer.com/").contains(".spot-thumbs > .right"),
        )
        assertTrue(
            merged.selectors("https://fuqer.com/").contains(".spot-thumbs > .right").not(),
        )
        assertTrue(
            uAssets.hidingRules.none { rule ->
                listOf(":matches-css", ":matches-attr", ":remove-attr").any {
                    it in rule.selector
                }
            },
        )
    }

    private companion object {
        const val RULE_COUNT_PREFIX = "# Rule count:"
    }

    private fun String.declaredValue(prefix: String): String = lineSequence()
        .first { it.startsWith(prefix) }
        .removePrefix(prefix)
        .trim()
}
