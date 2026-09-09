package dev.sk2andy.materialbrowser.browser.gecko

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoExtensionChromeRulesTest {
    @Test
    fun `action key exposes unique Bundle-saveable identity`() {
        val defaultAction = GeckoExtensionActionKey(
            extensionId = "extension@example.test",
            tabId = null,
            kind = GeckoExtensionActionKind.Browser,
        )
        val tabAction = defaultAction.copy(tabId = "tab-1")
        val pageAction = defaultAction.copy(kind = GeckoExtensionActionKind.Page)

        assertEquals(
            3,
            setOf(defaultAction.saveableId, tabAction.saveableId, pageAction.saveableId).size,
        )
    }

    @Test
    fun `session action overrides default fields but inherits missing presentation`() {
        val default = action(
            tabId = null,
            title = "Default",
            badgeText = "4",
            badgeBackgroundColor = 0xff00ff,
        )
        val session = action(tabId = "tab", title = "", badgeText = null)

        val resolved = GeckoExtensionChromeRules.resolveAction(default, session)

        assertEquals("Default", resolved?.title)
        assertEquals("4", resolved?.badgeText)
        assertEquals(0xff00ff, resolved?.badgeBackgroundColor)
        assertEquals("tab", resolved?.key?.tabId)
    }

    @Test
    fun `action input rejects control characters and unbounded badges`() {
        assertNull(
            GeckoExtensionChromeRules.normalizeAction(
                key = actionKey(),
                title = "Unsafe\ntitle",
                enabled = true,
                badgeText = null,
                badgeBackgroundColor = null,
                badgeTextColor = null,
            ),
        )
        assertNull(
            GeckoExtensionChromeRules.normalizeAction(
                key = actionKey(),
                title = "Safe",
                enabled = true,
                badgeText = "x".repeat(65),
                badgeBackgroundColor = null,
                badgeTextColor = null,
            ),
        )
    }

    @Test
    fun `create tab accepts bounded web address and nonnegative index`() {
        val accepted = GeckoExtensionChromeRules.validateCreateTab(
            request = createRequest(url = "https://example.com/a/../b", index = 2),
            extension = extension(),
        )

        assertEquals(
            "https://example.com/b",
            (accepted as GeckoExtensionTabRequestResult.Allowed).value.url,
        )
        assertEquals(2, accepted.value.index)
    }

    @Test
    fun `create tab rejects negative index and unsupported container`() {
        val negativeIndex = GeckoExtensionChromeRules.validateCreateTab(
            request = createRequest(url = "https://example.com", index = -1),
            extension = extension(),
        )
        val rejected = GeckoExtensionChromeRules.validateCreateTab(
            request = createRequest(url = "https://example.com", cookieStoreId = "firefox-container-1"),
            extension = extension(),
        )

        assertEquals(
            GeckoExtensionTabRequestRejection.UnsupportedDetail,
            (negativeIndex as GeckoExtensionTabRequestResult.Rejected).reason,
        )
        assertEquals(
            GeckoExtensionTabRequestRejection.UnsupportedDetail,
            (rejected as GeckoExtensionTabRequestResult.Rejected).reason,
        )
    }

    @Test
    fun `extension page navigation is restricted to requesting extension origin`() {
        val own = GeckoExtensionChromeRules.validateCreateTab(
            request = createRequest(url = "moz-extension://fixture-uuid/page.html"),
            extension = extension(baseUrl = "moz-extension://fixture-uuid/"),
        )
        val foreign = GeckoExtensionChromeRules.validateCreateTab(
            request = createRequest(url = "moz-extension://other-uuid/page.html"),
            extension = extension(baseUrl = "moz-extension://fixture-uuid/"),
        )

        assertTrue(own is GeckoExtensionTabRequestResult.Allowed)
        assertEquals(
            GeckoExtensionTabRequestRejection.InvalidUrl,
            (foreign as GeckoExtensionTabRequestResult.Rejected).reason,
        )
    }

    @Test
    fun `private extension tab request requires explicit private allowance`() {
        val source = identity(isPrivate = true)

        val result = GeckoExtensionChromeRules.validateCreateTab(
            request = createRequest(source = source),
            extension = extension(allowedInPrivateBrowsing = false),
        )

        assertEquals(
            GeckoExtensionTabRequestRejection.PrivateAccessDenied,
            (result as GeckoExtensionTabRequestResult.Rejected).reason,
        )
    }

    @Test
    fun `update and close reject replaced session generation`() {
        val requested = identity(generation = 3)
        val current = requested.copy(generation = 4)

        val update = GeckoExtensionChromeRules.validateUpdateTab(
            request = GeckoExtensionUpdateTabRequest(
                extensionId = EXTENSION_ID,
                target = requested,
                url = "https://example.com/updated",
                active = true,
                pinned = null,
                highlighted = null,
                muted = null,
                autoDiscardable = null,
            ),
            extension = extension(),
            currentTarget = current,
        )

        assertEquals(
            GeckoExtensionTabRequestRejection.StaleSession,
            (update as GeckoExtensionTabRequestResult.Rejected).reason,
        )
        assertEquals(
            GeckoExtensionTabRequestRejection.StaleSession,
            GeckoExtensionChromeRules.mayCloseTab(extension(), requested, current),
        )
    }

    @Test
    fun `update accepts per tab mute and rejects unsupported tab model fields`() {
        val target = identity()
        val muted = GeckoExtensionChromeRules.validateUpdateTab(
            request = updateRequest(target = target, muted = true),
            extension = extension(),
            currentTarget = target,
        )
        val highlighted = GeckoExtensionChromeRules.validateUpdateTab(
            request = updateRequest(target = target, highlighted = true),
            extension = extension(),
            currentTarget = target,
        )
        val autoDiscardable = GeckoExtensionChromeRules.validateUpdateTab(
            request = updateRequest(target = target, autoDiscardable = false),
            extension = extension(),
            currentTarget = target,
        )

        assertTrue(muted is GeckoExtensionTabRequestResult.Allowed)
        assertEquals(
            GeckoExtensionTabRequestRejection.UnsupportedDetail,
            (highlighted as GeckoExtensionTabRequestResult.Rejected).reason,
        )
        assertEquals(
            GeckoExtensionTabRequestRejection.UnsupportedDetail,
            (autoDiscardable as GeckoExtensionTabRequestResult.Rejected).reason,
        )
    }

    @Test
    fun `extension mute override is tab scoped and wins over domain preference`() {
        assertTrue(
            GeckoExtensionChromeRules.effectiveAudioMuted(
                domainMuted = true,
                extensionOverride = null,
            ),
        )
        assertFalse(
            GeckoExtensionChromeRules.effectiveAudioMuted(
                domainMuted = true,
                extensionOverride = false,
            ),
        )
        assertTrue(
            GeckoExtensionChromeRules.effectiveAudioMuted(
                domainMuted = false,
                extensionOverride = true,
            ),
        )
    }

    @Test
    fun `popup dies on owner switch disable or private revocation`() {
        val owner = identity(isPrivate = true)
        val popup = GeckoExtensionPopupIdentity(
            extensionId = EXTENSION_ID,
            owner = owner,
            generation = 8,
        )

        assertTrue(
            GeckoExtensionChromeRules.mayKeepPopup(
                popup,
                owner,
                extension(allowedInPrivateBrowsing = true),
            ),
        )
        assertFalse(
            GeckoExtensionChromeRules.mayKeepPopup(
                popup,
                owner.copy(tabId = "other"),
                extension(allowedInPrivateBrowsing = true),
            ),
        )
        assertFalse(
            GeckoExtensionChromeRules.mayKeepPopup(
                popup,
                owner,
                extension(enabled = false, allowedInPrivateBrowsing = true),
            ),
        )
        assertFalse(
            GeckoExtensionChromeRules.mayKeepPopup(popup, owner, extension()),
        )
    }

    @Test
    fun `options page must share exact moz extension origin`() {
        assertEquals(
            "moz-extension://fixture-uuid/options/index.html",
            GeckoExtensionChromeRules.normalizeOptionsPageUrl(
                baseUrl = "moz-extension://fixture-uuid/",
                optionsPageUrl = "moz-extension://fixture-uuid/options/./index.html",
            ),
        )
        assertNull(
            GeckoExtensionChromeRules.normalizeOptionsPageUrl(
                baseUrl = "moz-extension://fixture-uuid/",
                optionsPageUrl = "https://example.com/options.html",
            ),
        )
        assertEquals(
            GeckoExtensionOptionsTarget(
                extensionId = EXTENSION_ID,
                title = "Fixture",
                url = "moz-extension://fixture-uuid/options.html",
            ),
            GeckoExtensionChromeRules.optionsPageTarget(
                extension().copy(
                    optionsPageUrl = "moz-extension://fixture-uuid/options.html",
                ),
            ),
        )
    }

    @Test
    fun `capability matrix names unsupported window boundary`() {
        val windows = GeckoExtensionChromeRules.capabilityMatrix.single { it.api == "windows API" }

        assertEquals(GeckoExtensionCapabilitySupport.Unsupported, windows.support)
        assertTrue(windows.evidence.contains("No GeckoView 155"))
    }

    @Test
    fun `capability matrix documents tab model limits`() {
        val create = GeckoExtensionChromeRules.capabilityMatrix.single { it.api == "tabs.create" }
        val update = GeckoExtensionChromeRules.capabilityMatrix.single {
            it.api == "tabs.update/remove"
        }
        val unsupported = GeckoExtensionChromeRules.capabilityMatrix.single {
            it.api == "tabs.update highlighted/autoDiscardable"
        }

        assertEquals(GeckoExtensionCapabilitySupport.CandyDelegate, create.support)
        assertTrue(create.evidence.contains("index"))
        assertEquals(GeckoExtensionCapabilitySupport.CandyDelegate, update.support)
        assertTrue(update.evidence.contains("URL"))
        assertEquals(GeckoExtensionCapabilitySupport.Unsupported, unsupported.support)
        val schemaUnsupported = GeckoExtensionChromeRules.capabilityMatrix.single {
            it.api == "tabs.update muted/pinned"
        }
        assertEquals(GeckoExtensionCapabilitySupport.Unsupported, schemaUnsupported.support)
        assertTrue(schemaUnsupported.evidence.contains("GeckoView 155"))
    }

    private fun action(
        tabId: String?,
        title: String,
        badgeText: String?,
        badgeBackgroundColor: Int? = null,
    ) = GeckoExtensionActionState(
        key = actionKey(tabId),
        title = title,
        enabled = true,
        badgeText = badgeText,
        badgeBackgroundColor = badgeBackgroundColor,
        badgeTextColor = null,
    )

    private fun actionKey(tabId: String? = null) = GeckoExtensionActionKey(
        extensionId = EXTENSION_ID,
        tabId = tabId,
        kind = GeckoExtensionActionKind.Browser,
    )

    private fun createRequest(
        url: String? = null,
        source: GeckoExtensionSessionIdentity? = identity(),
        index: Int? = null,
        cookieStoreId: String? = null,
    ) = GeckoExtensionCreateTabRequest(
        extensionId = EXTENSION_ID,
        source = source,
        url = url,
        active = true,
        pinned = false,
        index = index,
        cookieStoreId = cookieStoreId,
        discarded = false,
        openInReaderMode = false,
    )

    private fun updateRequest(
        target: GeckoExtensionSessionIdentity,
        highlighted: Boolean? = null,
        muted: Boolean? = null,
        autoDiscardable: Boolean? = null,
    ) = GeckoExtensionUpdateTabRequest(
        extensionId = EXTENSION_ID,
        target = target,
        url = null,
        active = null,
        pinned = null,
        highlighted = highlighted,
        muted = muted,
        autoDiscardable = autoDiscardable,
    )

    private fun identity(
        tabId: String = "tab",
        isPrivate: Boolean = false,
        generation: Long = 1,
    ) = GeckoExtensionSessionIdentity(
        tabId = tabId,
        profileId = "default",
        isPrivate = isPrivate,
        generation = generation,
    )

    private fun extension(
        enabled: Boolean = true,
        allowedInPrivateBrowsing: Boolean = false,
        baseUrl: String? = "moz-extension://fixture-uuid/",
    ) = GeckoExtension(
        id = EXTENSION_ID,
        name = "Fixture",
        version = "1.0",
        enabled = enabled,
        allowedInPrivateBrowsing = allowedInPrivateBrowsing,
        isBuiltIn = false,
        baseUrl = baseUrl,
    )

    private companion object {
        const val EXTENSION_ID = "fixture@example.com"
    }
}
