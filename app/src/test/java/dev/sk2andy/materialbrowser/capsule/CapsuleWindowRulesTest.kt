package dev.sk2andy.materialbrowser.capsule

import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.ProfileLockTrigger
import dev.sk2andy.materialbrowser.browser.ProfileProtection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapsuleWindowRulesTest {
    private val profile = BrowserProfile("work", "📬")
    private val capsule = SiteCapsule(
        id = "04a74ad8-7533-460c-bfbf-a135968940d5",
        name = "Mail",
        startUrl = "https://mail.example",
        profileId = profile.id,
        opensInOwnWindow = true,
        createdAtMillis = 1L,
        updatedAtMillis = 1L,
    )

    @Test
    fun `own windows require explicit opt in and existing local unprotected owner`() {
        assertTrue(CapsuleWindowRules.canOpen(capsule, listOf(profile)))
        assertFalse(CapsuleWindowRules.canOpen(capsule.copy(opensInOwnWindow = false), listOf(profile)))
        assertFalse(CapsuleWindowRules.canOpen(capsule, emptyList()))
        assertFalse(CapsuleWindowRules.canOpen(capsule, listOf(profile.copy(syncedDeviceId = "remote"))))
        assertFalse(CapsuleWindowRules.canOpen(
            capsule,
            listOf(profile.copy(protection = ProfileProtection(ProfileLockTrigger.AppBackgrounded))),
        ))
    }
}
