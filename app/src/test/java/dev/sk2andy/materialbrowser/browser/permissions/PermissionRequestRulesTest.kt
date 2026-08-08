package dev.sk2andy.materialbrowser.browser.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionRequestRulesTest {
    private val identity = PermissionRequestIdentity(
        tabId = "tab-a",
        profileId = "personal",
        origin = "https://example.com",
        navigationGeneration = 7,
        isPrivate = false,
    )

    @Test
    fun decisionMatrixSeparatesAskAllowAndBlockWithSessionOverride() {
        val decisions = mapOf(
            SitePermission.Camera to SitePermissionDecision.Allow,
            SitePermission.Microphone to SitePermissionDecision.Block,
            SitePermission.Location to SitePermissionDecision.Ask,
        )

        val matrix = PermissionRequestRules.decisions(
            permissions = decisions.keys,
            decisionFor = decisions::getValue,
            allowedForSession = { it == SitePermission.Location },
        )

        assertEquals(setOf(SitePermission.Camera, SitePermission.Location), matrix.allowed)
        assertTrue(matrix.pending.isEmpty())
        assertEquals(setOf(SitePermission.Microphone), matrix.blocked)
    }

    @Test
    fun runtimeDenialRemovesOnlyRuntimeBackedPermission() {
        val granted = PermissionRequestRules.afterRuntimeResult(
            allowed = setOf(SitePermission.Camera, SitePermission.ProtectedMedia),
            runtimeGranted = { false },
        )

        assertEquals(setOf(SitePermission.ProtectedMedia), granted)
    }

    @Test
    fun navigationRaceInvalidatesPendingRequestButSecureEmbeddedOriginIsAllowed() {
        assertTrue(PermissionRequestRules.isCurrent(identity, currentState()))
        assertFalse(
            PermissionRequestRules.isCurrent(
                identity,
                currentState().copy(navigationGeneration = 8),
            ),
        )
        assertTrue(
            PermissionRequestRules.isCurrent(
                identity,
                currentState().copy(topLevelOrigin = "https://embedder.example"),
            ),
        )
    }

    @Test
    fun insecureRequesterOrTopLevelOriginIsRejected() {
        assertFalse(
            PermissionRequestRules.isCurrent(
                identity.copy(origin = "http://requester.example"),
                currentState(),
            ),
        )
        assertFalse(
            PermissionRequestRules.isCurrent(
                identity,
                currentState().copy(topLevelOrigin = "http://embedder.example"),
            ),
        )
    }

    @Test
    fun backgroundLifecycleAndPrivateModeMismatchInvalidateRequest() {
        assertFalse(PermissionRequestRules.isCurrent(identity, currentState().copy(isSelected = false)))
        assertFalse(
            PermissionRequestRules.isCurrent(
                identity,
                currentState().copy(isActivityResumed = false),
            ),
        )
        assertFalse(PermissionRequestRules.isCurrent(identity, currentState().copy(isPrivate = true)))
        assertFalse(PermissionRequestRules.isCurrent(identity, currentState().copy(tabExists = false)))
    }

    private fun currentState() = PermissionRequestState(
        tabId = identity.tabId,
        profileId = identity.profileId,
        topLevelOrigin = identity.origin,
        navigationGeneration = identity.navigationGeneration,
        isPrivate = identity.isPrivate,
        isSelected = true,
        isActivityResumed = true,
        tabExists = true,
    )
}
