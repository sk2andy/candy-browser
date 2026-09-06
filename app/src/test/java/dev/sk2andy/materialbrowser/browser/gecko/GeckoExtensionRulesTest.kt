package dev.sk2andy.materialbrowser.browser.gecko

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoExtensionRulesTest {
    @Test
    fun `internal Candy hosts are hidden from user extension manager`() {
        listOf(
            CandyPrivacyHostContract.EXTENSION_ID,
            CandyToppingHostContract.EXTENSION_ID,
        ).forEach { extensionId ->
            assertFalse(GeckoExtensionRules.isVisibleToUserManager(extensionId))
        }
        assertTrue(GeckoExtensionRules.isVisibleToUserManager("addon@example.com"))
    }

    @Test
    fun `signed XPI URI accepts normalized HTTPS without credentials`() {
        assertEquals(
            "https://addons.mozilla.org/firefox/downloads/file/addon.xpi?src=candy",
            GeckoExtensionRules.normalizeSignedXpiUri(
                "https://addons.mozilla.org/firefox/downloads/collection/../file/addon.xpi?src=candy",
            ),
        )
        assertEquals(
            "https://example.com:443/addon",
            GeckoExtensionRules.normalizeSignedXpiUri("https://example.com:443/addon"),
        )
    }

    @Test
    fun `signed XPI URI rejects transport and authority ambiguity`() {
        listOf(
            "http://addons.mozilla.org/addon.xpi",
            "https://user:password@example.com/addon.xpi",
            "https://example.com:8443/addon.xpi",
            "https://example.com/addon.xpi#fragment",
            "https:///addon.xpi",
            " https://example.com/addon.xpi",
        ).forEach { uri ->
            assertNull(uri, GeckoExtensionRules.normalizeSignedXpiUri(uri))
        }
    }

    @Test
    fun `private context cannot mutate runtime-global extensions`() {
        assertEquals(
            GeckoExtensionRejection.PrivateManagementContext,
            GeckoExtensionRules.managementRejection(context(isPrivate = true)),
        )
        assertNull(GeckoExtensionRules.managementRejection(context(isPrivate = false)))
    }

    @Test
    fun `permission request is bounded deduplicated and deterministic`() {
        val normalized = requireNotNull(
            GeckoExtensionRules.normalizePermissionRequest(
                permissionRequest(
                    permissions = listOf("tabs", "storage", "tabs"),
                    origins = listOf("https://b.example/*", "https://a.example/*"),
                ),
            ),
        )

        assertEquals(listOf("storage", "tabs"), normalized.permissions)
        assertEquals(
            listOf("https://a.example/*", "https://b.example/*"),
            normalized.origins,
        )
    }

    @Test
    fun `denied permission decision cannot retain private grant`() {
        assertEquals(
            GeckoExtensionPermissionDecision.Denied,
            GeckoExtensionRules.restrictPermissionDecision(
                permissionRequest(),
                GeckoExtensionPermissionDecision(
                    grantPermissions = false,
                    allowInPrivateBrowsing = true,
                ),
            ),
        )
    }

    @Test
    fun `update permission decision cannot change private access`() {
        val restricted = GeckoExtensionRules.restrictPermissionDecision(
            permissionRequest(kind = GeckoExtensionPermissionRequestKind.Update),
            GeckoExtensionPermissionDecision(
                grantPermissions = true,
                allowInPrivateBrowsing = true,
            ),
        )

        assertTrue(restricted.grantPermissions)
        assertEquals(false, restricted.allowInPrivateBrowsing)
    }

    private fun context(isPrivate: Boolean) = GeckoExtensionManagementContext(
        profileId = "profile",
        isPrivate = isPrivate,
    )

    private fun permissionRequest(
        kind: GeckoExtensionPermissionRequestKind = GeckoExtensionPermissionRequestKind.Install,
        permissions: List<String> = listOf("tabs"),
        origins: List<String> = listOf("https://example.com/*"),
    ) = GeckoExtensionPermissionRequest(
        kind = kind,
        extensionId = "addon@example.com",
        extensionName = "Addon",
        permissions = permissions,
        origins = origins,
    )
}
