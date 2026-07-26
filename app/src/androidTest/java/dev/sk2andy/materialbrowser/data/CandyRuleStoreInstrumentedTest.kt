package dev.sk2andy.materialbrowser.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.blocking.CandyRule
import dev.sk2andy.materialbrowser.blocking.CandyRuleAction
import dev.sk2andy.materialbrowser.blocking.CandyRuleKind
import dev.sk2andy.materialbrowser.blocking.CandyRuleOrigin
import dev.sk2andy.materialbrowser.blocking.CandyRuleValidator
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CandyRuleStoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun atomicallyRoundTripsRulesAndRejectsCorruptOversizeState() {
        val store = CandyRuleStore(context)
        store.clear()
        val rule = CandyRule.new(
            action = CandyRuleAction.Block,
            kind = CandyRuleKind.HostPair,
            requestHost = "tracker.example",
            firstPartyHost = "news.example",
            profileId = "work",
        )

        assertTrue(store.save(listOf(rule)))
        assertEquals(listOf(rule), store.load())

        File(context.noBackupFilesDir, CandyRuleStore.FILE_NAME).writeBytes(
            ByteArray(CandyRuleStore.MAX_FILE_BYTES + 1),
        )
        assertTrue(store.load().isEmpty())
        assertTrue(!File(context.noBackupFilesDir, CandyRuleStore.FILE_NAME).exists())
    }

    @Test
    fun maximumRuleCountWithBoundedSubscriptionUrlsPersists() {
        val store = CandyRuleStore(context)
        store.clear()
        val source = "https://lists.example/" + "a".repeat(2_000)
        val rules = List(CandyRuleValidator.MAX_RULES) { index ->
            CandyRule(
                id = "rule-$index",
                action = CandyRuleAction.Block,
                kind = CandyRuleKind.RequestHost,
                requestHost = "tracker$index.example",
                group = "List",
                origin = CandyRuleOrigin.Subscription,
                sourceUrl = source,
            )
        }

        assertTrue(store.save(rules))
        assertEquals(CandyRuleValidator.MAX_RULES, store.load().size)
        store.clear()
    }
}
