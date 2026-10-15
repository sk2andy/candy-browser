package dev.sk2andy.materialbrowser.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.integration.linkrouting.LinkRoutingRule
import dev.sk2andy.materialbrowser.browser.integration.linkrouting.LinkRoutingTarget
import dev.sk2andy.materialbrowser.browser.integration.linkrouting.LinkRoutingTargetKind
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LinkRoutingStoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = File(context.filesDir, LinkRoutingStore.FILE_NAME)

    @Before
    @After
    fun clearStore() {
        file.delete()
        File(file.path + ".bak").delete()
    }

    @Test
    fun configuredRulesPreserveOrderTargetsAndEnabledState() {
        val rules = listOf(
            LinkRoutingRule("work", "github.com", target = LinkRoutingTarget(LinkRoutingTargetKind.Profile, "work")),
            LinkRoutingRule("news", "news.example", includeSubdomains = true, target = LinkRoutingTarget(LinkRoutingTargetKind.Reader), enabled = false),
            LinkRoutingRule("capsule", "home.example", target = LinkRoutingTarget(LinkRoutingTargetKind.Capsule, "home")),
        )

        LinkRoutingStore(context).save(rules)

        assertEquals(rules, LinkRoutingStore(context).load())
    }

    @Test
    fun malformedOversizedAndUnknownVersionDataFailsClosed() {
        file.writeText("not JSON")
        assertTrue(LinkRoutingStore(context).load().isEmpty())
        file.writeText("""{"version":2,"rules":[]}""")
        assertTrue(LinkRoutingStore(context).load().isEmpty())
        file.writeBytes(ByteArray(64 * 1024 + 1))
        assertTrue(LinkRoutingStore(context).load().isEmpty())
    }

    @Test
    fun unknownTargetKindsDoNotBecomeDefaultRoutes() {
        file.writeText("""{"version":1,"rules":[{"id":"bad","domain":"news.example","target":"future"},{"id":"good","domain":"news.example","target":"reader","targetId":null}]}""")

        val rules = LinkRoutingStore(context).load()

        assertEquals(listOf("good"), rules.map(LinkRoutingRule::id))
        assertEquals(LinkRoutingTarget(LinkRoutingTargetKind.Reader), rules.single().target)
    }
}
