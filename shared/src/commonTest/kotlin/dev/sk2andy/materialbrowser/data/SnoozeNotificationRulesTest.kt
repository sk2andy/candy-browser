package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.ProfileLockTrigger
import dev.sk2andy.materialbrowser.browser.ProfileProtection
import kotlin.test.Test
import kotlin.test.assertEquals

class SnoozeNotificationRulesTest {
    @Test
    fun `notification content excludes protected and private sources independently of lock timing`() {
        val profiles = listOf(
            BrowserProfile("candy", "🍬"),
            BrowserProfile("protected", "💼", protection = ProfileProtection(ProfileLockTrigger.Cooldown, 1)),
        )
        val public = BrowserTab("public", 1)
        val protected = BrowserTab("protected", 1, profileId = "protected")
        val private = BrowserTab("private", 1, isIncognito = true)
        val unrelated = BrowserTab("unrelated", 1)
        assertEquals(listOf(public), SnoozeNotificationRules.eligibleTabs(
            listOf(public, protected, private, unrelated), setOf("public", "protected", "private"), profiles,
        ))
        val pending = listOf(public, protected, private).map { SnoozedTab(it, 200, 100) }
        assertEquals(listOf(pending.first()), SnoozeNotificationRules.eligibleSnoozedTabs(pending, profiles))
    }
}
