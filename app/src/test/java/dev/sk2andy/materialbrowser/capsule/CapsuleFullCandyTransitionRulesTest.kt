package dev.sk2andy.materialbrowser.capsule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CapsuleFullCandyTransitionRulesTest {
    @Test
    fun `explicit full Candy keeps current regular capsule tab and profile`() {
        assertEquals(
            CapsuleFullCandyTransition.KeepCurrentTab(TAB_ID, PROFILE_ID),
            resolve(),
        )
    }

    @Test
    fun `out of scope web navigation opens target in new tab with capsule profile`() {
        assertEquals(
            CapsuleFullCandyTransition.OpenTargetInNewTab(
                sourceTabId = TAB_ID,
                profileId = PROFILE_ID,
                targetUrl = "https://outside.example/path",
            ),
            resolve(targetUrl = "https://outside.example/path"),
        )
    }

    @Test
    fun `stale private wrong profile and unsafe transitions are rejected`() {
        assertNull(resolve(activeCapsuleTabId = "other-tab"))
        assertNull(resolve(selectedTabIsPrivate = true))
        assertNull(resolve(selectedProfileId = "other-profile"))
        assertNull(resolve(targetUrl = "intent://outside.example"))
    }

    private fun resolve(
        activeCapsuleTabId: String? = TAB_ID,
        selectedProfileId: String = PROFILE_ID,
        selectedTabIsPrivate: Boolean = false,
        targetUrl: String? = null,
    ): CapsuleFullCandyTransition? = CapsuleFullCandyTransitionRules.resolve(
        capsule = capsule,
        activeCapsuleTabId = activeCapsuleTabId,
        selectedTabId = TAB_ID,
        selectedProfileId = selectedProfileId,
        selectedTabIsPrivate = selectedTabIsPrivate,
        targetUrl = targetUrl,
    )

    private companion object {
        const val TAB_ID = "capsule-tab"
        const val PROFILE_ID = "work"
        val capsule = SiteCapsule(
            id = "04a74ad8-7533-460c-bfbf-a135968940d5",
            name = "Work",
            startUrl = "https://capsule.example/start",
            profileId = PROFILE_ID,
            createdAtMillis = 1L,
            updatedAtMillis = 1L,
        )
    }
}
