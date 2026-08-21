package dev.sk2andy.materialbrowser.blocking

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UassetsFilterAssetInstrumentedTest {
    @Test
    fun builtInSnapshotIsActiveWithoutAUserImport() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val blocker = ContentBlocker(context)

        assertTrue(
            blocker.shouldBlock(
                "https://cdn.doathair.com/ad.js",
                "https://news.example/article",
            ),
        )
        assertTrue(
            blocker.shouldBlock(
                "https://exmarketplace.com/ad.js",
                "https://other.example/article",
            ),
        )
        assertFalse(
            blocker.shouldBlock(
                "https://exmarketplace.com/ad.js",
                "https://www.tvserial.it/show",
            ),
        )
    }

    @Test
    fun compiledAssetsMatchPinnedSourceThroughProductionParser() {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        val source = assets.open("uassets_filters_source.txt").bufferedReader().use { it.readText() }
        val preview = CandySubscriptionRules.validatePreview(
            CandyFilterPresets.UBLOCK_ORIGIN_BASE_URL,
            source,
        )

        assertTrue(preview.isApplicable)
        assertEquals(49, preview.rules.size)
        assertEquals(
            preview.rules
                .filter { it.action == CandyRuleAction.Block && it.kind == CandyRuleKind.RequestHost }
                .mapNotNull { it.requestHost }
                .sorted(),
            assetLines("uassets_blocked_hosts.txt"),
        )
        assertEquals(
            preview.rules
                .filter { it.action == CandyRuleAction.Block && it.kind == CandyRuleKind.HostPair }
                .map { "${it.requestHost}\t${it.firstPartyHost}" }
                .sorted(),
            assetLines("uassets_blocked_host_pairs.txt"),
        )
        assertEquals(
            preview.rules
                .filter { it.action == CandyRuleAction.Allow }
                .map {
                    "${it.requestHost}\t${it.firstPartyHost ?: "*"}"
                }
                .sorted(),
            assetLines("uassets_allowed_host_pairs.txt"),
        )
    }

    @Test
    fun advancedSnapshotBlocksPathsPopupsAndBuildsProceduralScript() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val blocker = ContentBlocker(context)
        blocker.awaitBundledBlockingForTesting()
        assertTrue(blocker.isBundledBlockingReady)
        assertTrue(ContentBlocker(context).isBundledBlockingReady)

        assertTrue(
            blocker.shouldBlock(
                "https://9anime.vip/banner/top.jpg",
                "https://9anime.vip/watch",
            ),
        )
        assertTrue(
            blocker.shouldBlockPopup(
                "https://bit.ly/click",
                "https://eurogamer.net/file",
            ),
        )
        assertTrue(blocker.windowOpenDefuserScript("https://dailyuploads.net/file").isNotEmpty())
        val proceduralScript = blocker.adProceduralDocumentStartScript(
            "https://aranzulla.it/article",
        )
        assertTrue(":remove" !in proceduralScript)
        assertTrue("node.remove()" in proceduralScript)
    }

    private fun assetLines(name: String): List<String> {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        return assets.open(name).bufferedReader().useLines { lines ->
            lines.map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith('#') }
                .sorted()
                .toList()
        }
    }
}
