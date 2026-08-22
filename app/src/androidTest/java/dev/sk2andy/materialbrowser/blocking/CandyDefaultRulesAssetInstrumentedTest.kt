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
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        val bytes = assets
            .open("easylist_blocked_hosts.txt")
            .use { it.readBytes() }

        val index = SortedHostIndex.from(bytes)

        assertEquals(100_377, index.size)
        assertEquals(true, "0.myikas.com" in index)
        assertEquals(true, "zzzmjfixezere.site" in index)
        assertEquals(false, "example.com" in index)

        val hagezi = SortedHostIndex.from(
            assets.open("hagezi_blocked_hosts.txt").use { it.readBytes() },
        )
        assertEquals(166_078, hagezi.size)
        assertEquals(true, "analyticsengine.s3.amazonaws.com" in hagezi)
        assertEquals(true, "zzzwowosss.com" in hagezi)
        assertEquals(false, "example.com" in hagezi)
    }

    @Test
    fun curatedHostAssetCoversTelemetryWithoutBlockingFunctionalPlatforms() {
        val hostRules = InstrumentationRegistry.getInstrumentation().targetContext.assets
            .open("blocked_hosts.txt")
            .bufferedReader()
            .use { it.readLines() }
        val blocker = RequestBlocker(hostRules.asSequence())

        listOf(
            "adjust.com",
            "kochava.com",
            "xp.apple.com",
        ).forEach { host ->
            assertEquals(host, true, blocker.shouldBlockHosts(host, "publisher.example"))
            assertEquals(host, false, blocker.shouldBlockHosts(host, host))
        }
        listOf(
            "consent.cookiebot.com",
            "inmobi.com",
            "graph.facebook.com",
            "redirector.googlevideo.com",
        ).forEach { host ->
            assertEquals(host, false, blocker.shouldBlockHosts(host, "publisher.example"))
        }
    }

    @Test
    fun bundledAssetCoversModernGoogleAndRedditAds() {
        val text = InstrumentationRegistry.getInstrumentation().targetContext.assets
            .open("easylist_cosmetic_rules.txt")
            .bufferedReader()
            .use { it.readText() }
        val bundled = EasyListCosmeticRules.parse(
            text,
            EasyListCosmeticRules.EASYLIST_V2_HEADER,
        )

        assertEquals(30_945, bundled.size)
        assertEquals(30_139, bundled.hidingRules.size)
        assertEquals(652, bundled.exceptionRules.size)
        assertEquals(154, bundled.genericHideExceptions.size)
        assertEquals(13_642, bundled.genericSelectors().size)
        assertEquals(34, bundled.hidingRules.count { it.hostPattern == "www.google.*" })
        assertTrue(bundled.exceptionRules.any {
            it.hostPattern == "ads.google.com" && it.selector == ".video-ads"
        })
        val google = bundled.scopedSelectors("https://www.google.com/search?q=hotel")
        assertTrue(google.toString(), "#tads[aria-label]" in google)
        assertTrue(google.toString(), "#google-s-ad" in google)
        assertTrue(google.toString(), "div[data-is-ad=\"1\"]" in google)
        assertTrue(bundled.scopedSelectors("https://www.google.de/search?q=hotel").isNotEmpty())
        assertTrue(bundled.scopedSelectors("https://www.google.fr/search?q=hotel").isNotEmpty())
        assertTrue(bundled.scopedSelectors("https://www.google.co.kr/search?q=hotel").isNotEmpty())
        assertTrue(bundled.scopedSelectors("https://www.google.com.sg/search?q=hotel").isNotEmpty())
        assertTrue(bundled.scopedSelectors("https://www.google.evil.com/search?q=hotel").isEmpty())
        assertTrue(bundled.scopedSelectors("https://www.google.com.de/search?q=hotel").isEmpty())
        assertTrue(bundled.scopedSelectors("https://mail.google.com/").isEmpty())
        assertTrue(bundled.scopedSelectors("https://maps.google.com/").isEmpty())
        assertTrue(bundled.scopedSelectors("https://accounts.google.com/").isEmpty())

        val reddit = bundled.scopedSelectors("https://www.reddit.com/r/popular/")
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

        val amazon = bundled.scopedSelectors("https://www.amazon.de/s?k=laptop")
        assertTrue(amazon.toString(), ".s-result-item:has(.puis-sponsored-label-text)" in amazon)
        assertTrue(
            amazon.toString(),
            "div[cel_widget_id^=\"MAIN-FEATURED_ASINS_LIST-\"]" in amazon,
        )
        assertTrue(bundled.scopedSelectors("https://www.amazon.co.jp/s?k=laptop").isNotEmpty())
        assertTrue(bundled.scopedSelectors("https://amazon.evil.com/s?k=laptop").isEmpty())
        assertTrue(bundled.scopedSelectors("https://amazon.com.de/s?k=laptop").isEmpty())

        val maximumHost = text.declaredValue("# Maximum resolved host:")
        val declaredMaximum = text.declaredValue("# Maximum resolved hide selectors:").toInt()
        val maximumSelectors = bundled.scopedSelectors("https://$maximumHost/")
        val worstScript = CandyCosmeticScript.create(maximumSelectors)
        assertTrue(
            "host=$maximumHost, selectors=${maximumSelectors.size}, declared=$declaredMaximum",
            maximumSelectors.size <= 256 && declaredMaximum >= bundled.genericSelectors().size,
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
            EasyListCosmeticRules.EASYLIST_V2_HEADER,
        )
        val uAssets = EasyListCosmeticRules.parse(
            assets.open("uassets_cosmetic_rules.txt").bufferedReader().use { it.readText() },
            EasyListCosmeticRules.UASSETS_HEADER,
        )
        val merged = EasyListCosmeticRules.merge(easyList, uAssets)

        assertEquals(8_630, uAssets.hidingRules.size)
        assertEquals(467, uAssets.exceptionRules.size)
        assertEquals(1_168, uAssets.genericHideExceptions.size)
        assertEquals(10_265, uAssets.size)
        assertEquals(38_725, merged.hidingRules.size)
        assertEquals(1_119, merged.exceptionRules.size)
        assertEquals(1_322, merged.genericHideExceptions.size)
        assertEquals(41_166, merged.size)
        val genericPayload = GenericCosmeticPayload.create(merged.genericSelectors())
        assertEquals(13_864, genericPayload.selectorCount)
        assertEquals(13_152, genericPayload.simpleSelectorCount)
        assertEquals(712, genericPayload.complexSelectorCount)
        assertTrue(genericPayload.encoded.length <= GenericCosmeticPayload.MAX_ENCODED_BYTES)
        assertTrue(".ad-space" in merged.genericSelectors())
        assertTrue(".ad-unit" in merged.genericSelectors())
        assertEquals(
            GenericCosmeticPolicy(disabled = true),
            merged.genericPolicy("https://adblockplus.org/"),
        )

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
