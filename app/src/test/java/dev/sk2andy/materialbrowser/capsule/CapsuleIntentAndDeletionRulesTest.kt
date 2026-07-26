package dev.sk2andy.materialbrowser.capsule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapsuleIntentAndDeletionRulesTest {
    @Test
    fun `intent resolves only exact action and stored opaque id`() {
        val capsule = capsule()

        assertEquals(
            CapsuleLaunchResolution.Open(capsule),
            CapsuleIntentRules.resolve(
                CapsuleIntentRules.ACTION_OPEN_CAPSULE,
                capsule.id,
                listOf(capsule),
            ),
        )
        assertEquals(
            CapsuleLaunchResolution.NormalHome,
            CapsuleIntentRules.resolve(
                CapsuleIntentRules.ACTION_OPEN_CAPSULE,
                "https://evil.example",
                listOf(capsule),
            ),
        )
        assertEquals(
            CapsuleLaunchResolution.NotCapsuleIntent,
            CapsuleIntentRules.resolve("android.intent.action.VIEW", capsule.id, listOf(capsule)),
        )
    }

    @Test
    fun `profile deletion needs ownership confirmation and no remaining capsule`() {
        val capsule = capsule().copy(ownsDedicatedProfile = true)
        val sharedOwner = capsule().copy(id = "0f574fca-8727-4392-b34f-c30a1ee5a058")

        assertFalse(CapsuleDeletionRules.plan(capsule, emptyList(), false).deleteDedicatedProfile)
        assertFalse(
            CapsuleDeletionRules.plan(capsule, listOf(sharedOwner), true).deleteDedicatedProfile,
        )
        assertTrue(CapsuleDeletionRules.plan(capsule, emptyList(), true).deleteDedicatedProfile)
    }

    @Test
    fun `shortcut projection is stable and contains no URL`() {
        val projection = CapsuleShortcutRules.project(capsule())

        assertEquals("site_capsule_04a74ad8-7533-460c-bfbf-a135968940d5", projection.shortcutId)
        assertFalse(projection.toString().contains("https://"))
    }

    private fun capsule() = SiteCapsule(
        id = "04a74ad8-7533-460c-bfbf-a135968940d5",
        name = "Example",
        startUrl = "https://example.com",
        profileId = "profile",
        createdAtMillis = 1L,
        updatedAtMillis = 1L,
    )
}
