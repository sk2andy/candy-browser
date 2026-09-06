package dev.sk2andy.materialbrowser.capsule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class SiteCapsuleRulesTest {
    @Test
    fun `no controls chrome mode round trips and hides all Candy overlays`() {
        assertEquals(
            CapsuleChromeMode.NoControls,
            CapsuleChromeMode.fromWireValue("no_controls"),
        )
        assertFalse(CapsuleChromeMode.NoControls.showsControls)
        assertTrue(CapsuleChromeMode.Minimal.showsControls)
        assertTrue(CapsuleChromeMode.Compact.showsControls)
    }

    @Test
    fun `creation limit is explicit and never silently evicts`() {
        assertTrue(SiteCapsuleRules.canCreate(63))
        assertFalse(SiteCapsuleRules.canCreate(64))
        assertFalse(SiteCapsuleRules.canCreate(65))
    }

    @Test
    fun `create normalizes bounded fields and rejects non web URLs`() {
        val capsule = SiteCapsuleRules.create(
            draft = draft(name = "  Candy Mail  ", url = "https://mail.example/path"),
            id = ID,
            nowMillis = 10L,
            multiProfileSupported = true,
        )

        assertEquals("Candy Mail", capsule?.name)
        assertEquals("https://mail.example/path", capsule?.startUrl)
        assertNull(
            SiteCapsuleRules.create(
                draft = draft(name = "Bad", url = "javascript:alert(1)"),
                id = ID,
                nowMillis = 10L,
                multiProfileSupported = true,
            ),
        )
    }

    @Test
    fun `icon customization is normalized independently from profile`() {
        val capsule = SiteCapsuleRules.create(
            draft = draft().copy(
                iconEmoji = "  🚀  ",
                iconColor = CapsuleIconColor.Charcoal,
            ),
            id = ID,
            nowMillis = 10L,
            multiProfileSupported = true,
        )
        val defaulted = SiteCapsuleRules.create(
            draft = draft().copy(iconEmoji = "   "),
            id = ID,
            nowMillis = 10L,
            multiProfileSupported = true,
        )

        assertEquals("🚀", capsule?.iconEmoji)
        assertEquals(CapsuleIconColor.Charcoal, capsule?.iconColor)
        assertEquals(SiteCapsuleRules.DEFAULT_ICON_EMOJI, defaulted?.iconEmoji)
        assertEquals(CapsuleIconColor.Light, CapsuleIconColor.fromWireValue("unknown"))
    }

    @Test
    fun `icon palette foregrounds meet accessible text contrast`() {
        CapsuleIconColor.entries.forEach { color ->
            assertTrue(
                "${color.wireValue} contrast",
                contrastRatio(color.foregroundArgb, color.backgroundArgb) >= 4.5,
            )
        }
    }

    @Test
    fun `isolation requires dedicated profile and provider support`() {
        val unsupported = SiteCapsuleRules.create(
            draft = draft().copy(ownsDedicatedProfile = true, isolatedStorageRequested = true),
            id = ID,
            nowMillis = 10L,
            multiProfileSupported = false,
        )
        val sharedProfile = SiteCapsuleRules.create(
            draft = draft().copy(ownsDedicatedProfile = false, isolatedStorageRequested = true),
            id = ID,
            nowMillis = 10L,
            multiProfileSupported = true,
        )
        val isolated = SiteCapsuleRules.create(
            draft = draft().copy(ownsDedicatedProfile = true, isolatedStorageRequested = true),
            id = ID,
            nowMillis = 10L,
            multiProfileSupported = true,
        )

        assertFalse(unsupported!!.isolatedStorageRequested)
        assertFalse(sharedProfile!!.isolatedStorageRequested)
        assertTrue(isolated!!.isolatedStorageRequested)
    }

    @Test
    fun `bounded keeps newest unique capsules`() {
        val capsules = (0 until SiteCapsuleRules.MAX_CAPSULES + 4).map { index ->
            capsule(id = "capsule_identifier_${index.toString().padStart(16, '0')}", updated = index.toLong())
        } + capsule(id = "capsule_identifier_${70.toString().padStart(16, '0')}", updated = 999L)

        val bounded = SiteCapsuleRules.bounded(capsules)

        assertEquals(SiteCapsuleRules.MAX_CAPSULES, bounded.size)
        assertEquals(999L, bounded.first().updatedAtMillis)
    }

    private fun draft(
        name: String = "Example",
        url: String = "https://example.com",
    ) = SiteCapsuleDraft(name = name, startUrl = url, profileId = "profile")

    private fun capsule(id: String, updated: Long) = SiteCapsule(
        id = id,
        name = "Example",
        startUrl = "https://example.com",
        profileId = "profile",
        createdAtMillis = 1L,
        updatedAtMillis = updated,
    )

    private fun contrastRatio(foreground: Long, background: Long): Double {
        val foregroundLuminance = relativeLuminance(foreground)
        val backgroundLuminance = relativeLuminance(background)
        return (max(foregroundLuminance, backgroundLuminance) + 0.05) /
            (min(foregroundLuminance, backgroundLuminance) + 0.05)
    }

    private fun relativeLuminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val value = ((argb shr shift) and 0xFF).toDouble() / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return channel(16) * 0.2126 + channel(8) * 0.7152 + channel(0) * 0.0722
    }

    private companion object {
        const val ID = "04a74ad8-7533-460c-bfbf-a135968940d5"
    }
}
