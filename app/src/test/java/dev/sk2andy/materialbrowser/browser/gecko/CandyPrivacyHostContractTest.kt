package dev.sk2andy.materialbrowser.browser.gecko

import dev.sk2andy.materialbrowser.blocking.CandyRule
import dev.sk2andy.materialbrowser.blocking.CandyRuleAction
import dev.sk2andy.materialbrowser.blocking.CandyRuleKind
import dev.sk2andy.materialbrowser.browser.BrowserViewportRect
import dev.sk2andy.materialbrowser.browser.InlineMediaPlayerMode
import dev.sk2andy.materialbrowser.data.GeckoSafeAreaSettings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandyPrivacyHostContractTest {
    @Test
    fun `privacy signals cross authenticated policy independently`() {
        val message = GeckoPrivacyPolicy.Disabled.copy(
            doNotTrackEnabled = false,
            globalPrivacyControlEnabled = true,
            privacySignalRevision = 7,
        ).toMessage(token = "session-token", revision = 4)

        assertFalse(message.getBoolean("doNotTrackEnabled"))
        assertTrue(message.getBoolean("globalPrivacyControlEnabled"))
        assertEquals(7L, message.getLong("privacySignalRevision"))
    }

    @Test
    fun `animation policy crosses authenticated policy with bounded revision`() {
        val policy = GeckoPrivacyPolicyRules.extensionOwnedAdFilteringWithCandyCookieDefaults(
            pageHost = null,
            pausedHosts = emptySet(),
            hideCookieConsent = false,
            cookieBannerRemovalDisabled = false,
            blockThirdPartyCookies = true,
            allowThirdPartyCookiesForSite = false,
            animationsEnabled = false,
            animationPolicyRevision = -7,
        )
        val message = policy.toMessage(token = "session-token", revision = 5)

        assertFalse(policy.animationsEnabled)
        assertEquals(0L, policy.animationPolicyRevision)
        assertFalse(message.getBoolean("animationsEnabled"))
        assertEquals(0L, message.getLong("animationPolicyRevision"))
    }

    @Test
    fun `bounded css safe area settings normalize independently from legacy inset`() {
        val policy = GeckoPrivacyPolicyRules.extensionOwnedAdFilteringWithCandyCookieDefaults(
            pageHost = null,
            pausedHosts = emptySet(),
            hideCookieConsent = false,
            cookieBannerRemovalDisabled = false,
            blockThirdPartyCookies = true,
            allowThirdPartyCookiesForSite = false,
            cssSafeAreaTopInsetPx = -1,
            geckoSafeAreaSettings = GeckoSafeAreaSettings(
                enabled = false,
                maxElementsPerBatch = Int.MAX_VALUE,
                mutationDebounceMillis = -1,
                interactionWindowMillis = 151,
            ),
        )

        assertEquals(0, policy.topInsetPx)
        assertEquals(0, policy.cssSafeAreaTopInsetPx)
        assertFalse(policy.geckoSafeAreaSettings.enabled)
        assertEquals(64, policy.geckoSafeAreaSettings.maxElementsPerBatch)
        assertEquals(50, policy.geckoSafeAreaSettings.mutationDebounceMillis)
        assertEquals(200, policy.geckoSafeAreaSettings.interactionWindowMillis)
        assertEquals(400, policy.safeAreaLayoutQuietPeriodMillis)
        assertEquals(3, policy.safeAreaRequiredFailureCount)
    }

    @Test
    fun `css safe area options cross the authenticated policy as bounded flat fields`() {
        val settings = GeckoSafeAreaSettings(
            recheckAddedElements = false,
            recheckChangedElements = false,
            requireInteractionForUpdates = false,
            recheckOnResize = false,
            interactionWindowMillis = 2000,
            mutationDebounceMillis = 250,
            maxElementsPerBatch = 24,
            maxBatchDurationMillis = 6,
            maxInitialElements = 768,
        )
        val message = GeckoPrivacyPolicy.Disabled.copy(
            cssSafeAreaTopInsetPx = 172,
            geckoSafeAreaSettings = settings,
        ).toMessage(token = "session-token", revision = 9)

        assertEquals(0, message.getInt("topInsetPx"))
        assertEquals(172, message.getInt("cssSafeAreaTopInsetPx"))
        assertTrue(message.getBoolean("geckoSafeAreaEnabled"))
        assertFalse(message.getBoolean("recheckAddedElements"))
        assertFalse(message.getBoolean("recheckChangedElements"))
        assertFalse(message.getBoolean("requireInteractionForUpdates"))
        assertFalse(message.getBoolean("recheckOnResize"))
        assertEquals(2000, message.getInt("interactionWindowMillis"))
        assertEquals(250, message.getInt("mutationDebounceMillis"))
        assertEquals(24, message.getInt("maxElementsPerBatch"))
        assertEquals(6, message.getInt("maxBatchDurationMillis"))
        assertEquals(768, message.getInt("maxInitialElements"))
    }

    @Test
    fun `extension owned ad filtering keeps only Candy cookie defaults`() {
        val policy = GeckoPrivacyPolicyRules.extensionOwnedAdFilteringWithCandyCookieDefaults(
            pageHost = "news.example",
            pausedHosts = setOf("paused.example"),
            hideCookieConsent = true,
            cookieBannerRemovalDisabled = false,
            blockThirdPartyCookies = true,
            allowThirdPartyCookiesForSite = false,
            topInsetPx = 96,
            navigationGeneration = 4,
            scrollMetricsEnabled = true,
            inlineMediaPlayerEnabled = true,
            inlineMediaPlayerMode = InlineMediaPlayerMode.Automatic.stableId,
            inlineMediaPlayerActionLabel = "Im Candy Player öffnen",
            inlineMediaPlayerPlayLabel = "Abspielen",
            inlineMediaPlayerPauseLabel = "Pausieren",
            inlineMediaPlayerSeekLabel = "Wiedergabeposition",
            inlineMediaPlayerEnterFullscreenLabel = "Video vergrößern",
            inlineMediaPlayerExitFullscreenLabel = "Video minimieren",
            inlineMediaPlayerShowControlsLabel = "Steuerelemente anzeigen",
            inlineMediaPlayerHideControlsLabel = "Steuerelemente ausblenden",
            safeAreaLayoutQuietPeriodMillis = 250,
            safeAreaRequiredFailureCount = 4,
        )

        assertFalse(policy.blockAdsAndTrackers)
        assertTrue(policy.hideCookieConsent)
        assertFalse(policy.cookieBannerRemovalDisabled)
        assertTrue(policy.candyRules.isEmpty())
        assertEquals("news.example", policy.pageHost)
        assertEquals(setOf("paused.example"), policy.pausedHosts)
        assertTrue(policy.blockThirdPartyCookies)
        assertFalse(policy.allowThirdPartyCookiesForSite)
        assertEquals(96, policy.topInsetPx)
        assertEquals(4, policy.navigationGeneration)
        assertTrue(policy.scrollMetricsEnabled)
        assertTrue(policy.inlineMediaPlayerEnabled)
        assertEquals(InlineMediaPlayerMode.Automatic.stableId, policy.inlineMediaPlayerMode)
        assertEquals("Im Candy Player öffnen", policy.inlineMediaPlayerActionLabel)
        assertEquals("Abspielen", policy.inlineMediaPlayerPlayLabel)
        assertEquals("Pausieren", policy.inlineMediaPlayerPauseLabel)
        assertEquals("Wiedergabeposition", policy.inlineMediaPlayerSeekLabel)
        assertEquals("Video vergrößern", policy.inlineMediaPlayerEnterFullscreenLabel)
        assertEquals("Video minimieren", policy.inlineMediaPlayerExitFullscreenLabel)
        assertEquals("Steuerelemente anzeigen", policy.inlineMediaPlayerShowControlsLabel)
        assertEquals("Steuerelemente ausblenden", policy.inlineMediaPlayerHideControlsLabel)
        assertEquals(
            "Steuerelemente anzeigen",
            policy.toMessage("session-token", 1).getString("inlineMediaPlayerShowControlsLabel"),
        )
        assertEquals(
            "Steuerelemente ausblenden",
            policy.toMessage("session-token", 1).getString("inlineMediaPlayerHideControlsLabel"),
        )
        assertEquals(
            "Im Candy Player öffnen",
            policy.toMessage("session-token", 1).getString("inlineMediaPlayerActionLabel"),
        )
        assertEquals(
            InlineMediaPlayerMode.Automatic.stableId,
            policy.toMessage("session-token", 1).getString("inlineMediaPlayerMode"),
        )
        assertEquals(
            "Abspielen",
            policy.toMessage("session-token", 1).getString("inlineMediaPlayerPlayLabel"),
        )
        assertEquals(
            "Pausieren",
            policy.toMessage("session-token", 1).getString("inlineMediaPlayerPauseLabel"),
        )
        assertEquals(
            "Wiedergabeposition",
            policy.toMessage("session-token", 1).getString("inlineMediaPlayerSeekLabel"),
        )
        assertEquals(
            "Video vergrößern",
            policy.toMessage("session-token", 1)
                .getString("inlineMediaPlayerEnterFullscreenLabel"),
        )
        assertEquals(
            "Video minimieren",
            policy.toMessage("session-token", 1)
                .getString("inlineMediaPlayerExitFullscreenLabel"),
        )
        assertEquals(250, policy.safeAreaLayoutQuietPeriodMillis)
        assertEquals(4, policy.safeAreaRequiredFailureCount)
    }

    @Test
    fun `safe area quiet period stays on the developer settings grid`() {
        val policy = GeckoPrivacyPolicyRules.extensionOwnedAdFilteringWithCandyCookieDefaults(
            pageHost = null,
            pausedHosts = emptySet(),
            hideCookieConsent = false,
            cookieBannerRemovalDisabled = false,
            blockThirdPartyCookies = true,
            allowThirdPartyCookiesForSite = false,
            safeAreaLayoutQuietPeriodMillis = 126,
        )

        assertEquals(150, policy.safeAreaLayoutQuietPeriodMillis)
    }

    @Test
    fun `only exact internal bootstrap navigation is trusted`() {
        val baseUrl = "moz-extension://trusted-origin/"
        val token = "session-token"

        assertTrue(
            CandyPrivacyHostContract.isTrustedBootstrapNavigation(
                url = "${baseUrl}bootstrap.html?token=$token",
                extensionBaseUrl = baseUrl,
                token = token,
                isDirectNavigation = true,
                hasUserGesture = false,
                isRedirect = false,
            ),
        )
        listOf(
            "moz-extension://other-origin/bootstrap.html?token=$token",
            "${baseUrl}other.html?token=$token",
            "${baseUrl}bootstrap.html?token=other-token",
        ).forEach { url ->
            assertFalse(
                CandyPrivacyHostContract.isTrustedBootstrapNavigation(
                    url = url,
                    extensionBaseUrl = baseUrl,
                    token = token,
                    isDirectNavigation = true,
                    hasUserGesture = false,
                    isRedirect = false,
                ),
            )
        }
    }

    @Test
    fun `gesture redirect and indirect bootstrap navigation remain untrusted`() {
        val baseUrl = "moz-extension://trusted-origin/"
        val url = "${baseUrl}bootstrap.html?token=session-token"

        listOf(
            Triple(false, false, false),
            Triple(true, true, false),
            Triple(true, false, true),
        ).forEach { (isDirectNavigation, hasUserGesture, isRedirect) ->
            assertFalse(
                CandyPrivacyHostContract.isTrustedBootstrapNavigation(
                    url = url,
                    extensionBaseUrl = baseUrl,
                    token = "session-token",
                    isDirectNavigation = isDirectNavigation,
                    hasUserGesture = hasUserGesture,
                    isRedirect = isRedirect,
                ),
            )
        }
    }

    @Test
    fun `session binding requires exact sender generation and an active bootstrap`() {
        fun accepted(
            nativeApp: String = CandyPrivacyHostContract.NATIVE_APP,
            extensionId: String = CandyPrivacyHostContract.EXTENSION_ID,
            environmentType: Int = 7,
            hasExpectedSession: Boolean = true,
            messageType: String = "bound",
            messageToken: String = "session-token",
            messageRevision: Long = 2,
            currentRevision: Long = 2,
            bootstrapStarted: Boolean = true,
        ) = CandyPrivacyHostContract.isTrustedSessionBindingMessage(
            nativeApp = nativeApp,
            extensionId = extensionId,
            environmentType = environmentType,
            expectedEnvironmentType = 7,
            hasExpectedSession = hasExpectedSession,
            messageType = messageType,
            messageToken = messageToken,
            expectedToken = "session-token",
            messageRevision = messageRevision,
            currentRevision = currentRevision,
            bootstrapStarted = bootstrapStarted,
        )

        assertTrue(accepted())
        assertTrue(accepted(messageRevision = 1, currentRevision = 2))
        assertFalse(accepted(nativeApp = "other.native.app"))
        assertFalse(accepted(extensionId = "other@extension"))
        assertFalse(accepted(environmentType = 8))
        assertFalse(accepted(hasExpectedSession = false))
        assertFalse(accepted(messageType = "policy-ready"))
        assertFalse(accepted(messageToken = "stale-token"))
        assertFalse(accepted(messageRevision = 3, currentRevision = 2))
        assertFalse(accepted(bootstrapStarted = false))
    }

    @Test
    fun `policy message preserves host pair allow precedence inputs deterministically`() {
        val message = GeckoPrivacyPolicy(
            pageHost = "news.example",
            blockAdsAndTrackers = true,
            hideCookieConsent = true,
            cookieBannerRemovalDisabled = false,
            pausedHosts = setOf("z.example", "a.example"),
            candyRules = listOf(
                rule(
                    id = "pair-allow",
                    action = CandyRuleAction.Allow,
                    kind = CandyRuleKind.HostPair,
                    requestHost = "tracker.example",
                    firstPartyHost = "news.example",
                ),
                rule(
                    id = "host-block",
                    action = CandyRuleAction.Block,
                    kind = CandyRuleKind.RequestHost,
                    requestHost = "tracker.example",
                ),
            ),
        ).toMessage(token = "session-token", revision = 7)

        assertEquals(CandyPrivacyHostContract.PROTOCOL_VERSION, message.getInt("protocolVersion"))
        assertEquals("session-token", message.getString("token"))
        assertEquals(7, message.getLong("revision"))
        assertEquals("news.example", message.getString("pageHost"))
        assertTrue(message.getBoolean("blockAds"))
        assertTrue(message.getBoolean("hideConsent"))
        assertFalse(message.getBoolean("cookieBannerRemovalDisabled"))
        assertEquals(0, message.getInt("topInsetPx"))
        assertFalse(message.has("viewportCoverAllowed"))
        assertEquals(0, message.getInt("navigationGeneration"))
        assertFalse(message.getBoolean("scrollMetricsEnabled"))
        assertFalse(message.getBoolean("inlineMediaPlayerEnabled"))
        assertEquals(400, message.getInt("safeAreaLayoutQuietPeriodMillis"))
        assertEquals(3, message.getInt("safeAreaRequiredFailureCount"))
        assertEquals(
            listOf(
                "accounts.google.com",
                "challenges.cloudflare.com",
                "js.hcaptcha.com",
                "www.google.com",
                "www.recaptcha.net",
            ),
            message.getJSONArray("compatibilityRequestHosts").strings(),
        )
        assertEquals(listOf("a.example", "z.example"), message.getJSONArray("pausedHosts").strings())
        assertEquals(listOf("host-block", "pair-allow"), message.getJSONArray("rules").ids())
        assertEquals("P", message.getJSONArray("rules").getJSONObject(1).getString("k"))
        assertEquals("A", message.getJSONArray("rules").getJSONObject(1).getString("a"))
    }

    @Test
    fun `policy separates active cosmetic rules from request rules`() {
        val message = GeckoPrivacyPolicy.Disabled.copy(
            candyRules = listOf(
                rule(
                    id = "inactive",
                    action = CandyRuleAction.Block,
                    kind = CandyRuleKind.RequestHost,
                    requestHost = "tracker.example",
                    active = false,
                ),
                rule(
                    id = "cosmetic",
                    action = CandyRuleAction.Cosmetic,
                    kind = CandyRuleKind.CosmeticCss,
                    firstPartyHost = "news.example",
                ),
                rule(
                    id = "inactive-cosmetic",
                    action = CandyRuleAction.Cosmetic,
                    kind = CandyRuleKind.CosmeticCss,
                    firstPartyHost = "news.example",
                    active = false,
                ),
            ),
        ).toMessage(token = "session-token", revision = 1)

        assertEquals(0, message.getJSONArray("rules").length())
        assertEquals(listOf("cosmetic"), message.getJSONArray("cosmetics").ids())
        assertEquals("news.example", message.getJSONArray("cosmetics").getJSONObject(0).getString("h"))
        assertEquals(".ad", message.getJSONArray("cosmetics").getJSONObject(0).getString("s"))
    }

    @Test
    fun `picture in picture playback message is scoped to one bound revision`() {
        val message = pictureInPicturePlaybackMessage(
            token = "session-token",
            revision = 7,
            expected = true,
        )

        assertEquals("picture-in-picture-playback", message.getString("type"))
        assertEquals(CandyPrivacyHostContract.PROTOCOL_VERSION, message.getInt("protocolVersion"))
        assertEquals("session-token", message.getString("token"))
        assertEquals(7, message.getLong("revision"))
        assertTrue(message.getBoolean("expected"))
    }

    @Test
    fun `picture in picture preparation accepts only current identity bound geometry`() {
        val identity = GeckoInlineVideoIdentity(
            documentNonce = "a".repeat(32),
            elementNonce = "b".repeat(32),
        )
        val message = JSONObject()
            .put("revision", 7)
            .put("navigationGeneration", 3)
            .put("requestId", 11)
            .put("prepared", true)
            .put("documentNonce", identity.documentNonce)
            .put("elementNonce", identity.elementNonce)
            .put("videoLeft", 20.0)
            .put("videoTop", 10.0)
            .put("videoRight", 380.0)
            .put("videoBottom", 210.0)
            .put("viewportWidth", 400.0)
            .put("viewportHeight", 240.0)

        assertEquals(
            GeckoPictureInPicturePreparation(
                identity = identity,
                videoRect = BrowserViewportRect(
                    leftFraction = 0.05f,
                    topFraction = 1f / 24f,
                    rightFraction = 0.95f,
                    bottomFraction = 0.875f,
                ),
            ),
            geckoPictureInPicturePreparationFromMessage(
                message = message,
                currentRevision = 7,
                currentNavigationGeneration = 3,
                expectedRequestId = 11,
                expectedIdentity = identity,
            ),
        )
        assertEquals(
            null,
            geckoPictureInPicturePreparationFromMessage(
                message = JSONObject(message.toString()).put("requestId", 12),
                currentRevision = 7,
                currentNavigationGeneration = 3,
                expectedRequestId = 11,
                expectedIdentity = identity,
            ),
        )
        assertEquals(
            null,
            geckoPictureInPicturePreparationFromMessage(
                message = JSONObject(message.toString()).put("videoTop", -1),
                currentRevision = 7,
                currentNavigationGeneration = 3,
                expectedRequestId = 11,
                expectedIdentity = identity,
            ),
        )
    }

    @Test
    fun `picture in picture restoration acknowledgement rejects stale return layouts`() {
        val request = pictureInPictureRestorationMessage(
            token = "session-token",
            revision = 7,
            navigationGeneration = 3,
            requestId = 11,
        )
        val result = JSONObject()
            .put("revision", 7)
            .put("navigationGeneration", 3)
            .put("requestId", 11)
            .put("prepared", true)

        assertFalse(request.getBoolean("expected"))
        assertTrue(
            isGeckoPictureInPictureRestorationResult(
                message = result,
                currentRevision = 7,
                currentNavigationGeneration = 3,
                expectedRequestId = 11,
            ),
        )
        assertFalse(
            isGeckoPictureInPictureRestorationResult(
                message = JSONObject(result.toString()).put("navigationGeneration", 4),
                currentRevision = 7,
                currentNavigationGeneration = 3,
                expectedRequestId = 11,
            ),
        )
    }

    @Test
    fun `new preparation acknowledgement wins over an older restoration request`() {
        val identity = GeckoInlineVideoIdentity(
            documentNonce = "a".repeat(32),
            elementNonce = "b".repeat(32),
        )
        val preparationResult = JSONObject()
            .put("revision", 7)
            .put("navigationGeneration", 3)
            .put("requestId", 12)
            .put("prepared", true)
            .put("documentNonce", identity.documentNonce)
            .put("elementNonce", identity.elementNonce)
            .put("videoLeft", 0)
            .put("videoTop", 0)
            .put("videoRight", 1_920)
            .put("videoBottom", 1_080)
            .put("viewportWidth", 1_920)
            .put("viewportHeight", 1_080)
        val staleRestorationResult = JSONObject(preparationResult.toString())
            .put("requestId", 11)

        assertEquals(
            GeckoPictureInPicturePlaybackResultRoute.Preparation,
            geckoPictureInPicturePlaybackResultRoute(
                message = preparationResult,
                preparationRequestId = 12,
                restorationRequestId = 11,
            ),
        )
        assertEquals(
            identity,
            geckoPictureInPicturePreparationFromMessage(
                message = preparationResult,
                currentRevision = 7,
                currentNavigationGeneration = 3,
                expectedRequestId = 12,
                expectedIdentity = identity,
            )?.identity,
        )
        assertEquals(
            GeckoPictureInPicturePlaybackResultRoute.Stale,
            geckoPictureInPicturePlaybackResultRoute(
                message = staleRestorationResult,
                preparationRequestId = 12,
                restorationRequestId = null,
            ),
        )
    }

    @Test
    fun `inline video state accepts current bounded top frame candidate`() {
        val state = geckoInlineVideoStateFromMessage(
            message = JSONObject()
                .put("revision", 7)
                .put("navigationGeneration", 3)
                .put("active", true)
                .put("playing", true)
                .put("presented", true)
                .put("videoWidth", 1_920)
                .put("videoHeight", 1_080)
                .put("documentNonce", "a".repeat(32))
                .put("elementNonce", "b".repeat(32)),
            currentRevision = 7,
            currentNavigationGeneration = 3,
        )

        assertEquals(
            GeckoInlineVideoState(
                isActive = true,
                isPlaying = true,
                isPresented = true,
                width = 1_920,
                height = 1_080,
                documentNonce = "a".repeat(32),
                elementNonce = "b".repeat(32),
            ),
            state,
        )
    }

    @Test
    fun `inline video state rejects stale malformed and unbounded candidates`() {
        fun message(
            revision: Long = 7,
            navigationGeneration: Int = 3,
            width: Int = 1_920,
            documentNonce: String = "a".repeat(32),
        ) = JSONObject()
            .put("revision", revision)
            .put("navigationGeneration", navigationGeneration)
            .put("active", true)
            .put("playing", true)
            .put("videoWidth", width)
            .put("videoHeight", 1_080)
            .put("documentNonce", documentNonce)
            .put("elementNonce", "b".repeat(32))

        assertEquals(null, geckoInlineVideoStateFromMessage(message(revision = 6), 7, 3))
        assertEquals(
            null,
            geckoInlineVideoStateFromMessage(message(navigationGeneration = 2), 7, 3),
        )
        assertEquals(null, geckoInlineVideoStateFromMessage(message(width = 16_385), 7, 3))
        assertEquals(
            null,
            geckoInlineVideoStateFromMessage(message(documentNonce = "invalid"), 7, 3),
        )
    }

    @Test
    fun `inline presentation desire clears only after presented video disappears`() {
        assertTrue(
            shouldClearInlineVideoPresentationDesire(
                wasPresented = true,
                isPresented = false,
                presentationDesired = true,
            ),
        )
        assertFalse(
            shouldClearInlineVideoPresentationDesire(
                wasPresented = false,
                isPresented = false,
                presentationDesired = true,
            ),
        )
        assertFalse(
            shouldClearInlineVideoPresentationDesire(
                wasPresented = true,
                isPresented = true,
                presentationDesired = true,
            ),
        )
        assertFalse(
            shouldClearInlineVideoPresentationDesire(
                wasPresented = true,
                isPresented = false,
                presentationDesired = false,
            ),
        )
    }

    @Test
    fun `inline video open request accepts only current bounded identity`() {
        val current = JSONObject()
            .put("revision", 7)
            .put("navigationGeneration", 3)
            .put("documentNonce", "a".repeat(32))
            .put("elementNonce", "b".repeat(32))

        assertEquals(
            GeckoInlineVideoOpenRequest(
                identity = GeckoInlineVideoIdentity(
                    documentNonce = "a".repeat(32),
                    elementNonce = "b".repeat(32),
                ),
                navigationGeneration = 3,
            ),
            geckoInlineVideoOpenRequestFromMessage(current, 7, 3),
        )
        assertEquals(
            null,
            geckoInlineVideoOpenRequestFromMessage(
                JSONObject(current.toString()).put("revision", 6),
                7,
                3,
            ),
        )
        assertEquals(
            false,
            geckoInlineVideoOpenRequestFromMessage(
                JSONObject(current.toString()).put("expected", false),
                7,
                3,
            )?.expected,
        )
        assertEquals(
            null,
            geckoInlineVideoOpenRequestFromMessage(
                JSONObject(current.toString()).put("navigationGeneration", 2),
                7,
                3,
            ),
        )
        assertEquals(
            null,
            geckoInlineVideoOpenRequestFromMessage(
                JSONObject(current.toString()).put("elementNonce", "page-controlled"),
                7,
                3,
            ),
        )
    }

    @Test
    fun `inline video gesture haptic accepts only current identity and semantic phase`() {
        val current = JSONObject()
            .put("revision", 7)
            .put("navigationGeneration", 3)
            .put("documentNonce", "a".repeat(32))
            .put("elementNonce", "b".repeat(32))
            .put("phase", "rubberband-start")

        assertEquals(
            GeckoInlineVideoGestureHaptic(
                identity = GeckoInlineVideoIdentity(
                    documentNonce = "a".repeat(32),
                    elementNonce = "b".repeat(32),
                ),
                navigationGeneration = 3,
                phase = GeckoInlineVideoGestureHapticPhase.RubberbandStart,
            ),
            geckoInlineVideoGestureHapticFromMessage(current, 7, 3),
        )
        assertEquals(
            GeckoInlineVideoGestureHapticPhase.RubberbandStop,
            geckoInlineVideoGestureHapticFromMessage(
                JSONObject(current.toString()).put("phase", "rubberband-stop"),
                7,
                3,
            )?.phase,
        )
        assertEquals(
            GeckoInlineVideoGestureHapticPhase.Confirm,
            geckoInlineVideoGestureHapticFromMessage(
                JSONObject(current.toString()).put("phase", "confirm"),
                7,
                3,
            )?.phase,
        )
        listOf(
            JSONObject(current.toString()).put("revision", 6),
            JSONObject(current.toString()).put("navigationGeneration", 2),
            JSONObject(current.toString()).put("elementNonce", "page-controlled"),
            JSONObject(current.toString()).put("phase", "pointer-delta"),
        ).forEach { invalid ->
            assertEquals(null, geckoInlineVideoGestureHapticFromMessage(invalid, 7, 3))
        }
    }

    @Test
    fun `scroll metrics accept current bounded document geometry`() {
        val metrics = geckoScrollMetricsFromMessage(
            message = JSONObject()
                .put("revision", 7)
                .put("offsetPx", 900)
                .put("extentPx", 1_000)
                .put("rangePx", 4_000),
            currentRevision = 7,
        )

        requireNotNull(metrics)
        assertEquals(900, metrics.offsetPx)
        assertEquals(1_000, metrics.extentPx)
        assertEquals(4_000, metrics.rangePx)
    }

    @Test
    fun `scroll metrics reject stale invalid and unbounded messages`() {
        fun message(revision: Long = 7, offset: Long = 0, extent: Long = 1, range: Long = 1) =
            JSONObject()
                .put("revision", revision)
                .put("offsetPx", offset)
                .put("extentPx", extent)
                .put("rangePx", range)

        assertEquals(null, geckoScrollMetricsFromMessage(message(revision = 6), 7))
        assertEquals(null, geckoScrollMetricsFromMessage(message(offset = -1), 7))
        assertEquals(null, geckoScrollMetricsFromMessage(message(extent = 0), 7))
        assertEquals(null, geckoScrollMetricsFromMessage(message(extent = 2, range = 1), 7))
        assertEquals(
            null,
            geckoScrollMetricsFromMessage(message(range = Int.MAX_VALUE.toLong() + 1), 7),
        )
    }

    private fun rule(
        id: String,
        action: CandyRuleAction,
        kind: CandyRuleKind,
        requestHost: String? = null,
        firstPartyHost: String? = null,
        active: Boolean = true,
    ) = CandyRule(
        id = id,
        action = action,
        kind = kind,
        requestHost = requestHost,
        firstPartyHost = firstPartyHost,
        cosmeticSelector = if (kind == CandyRuleKind.CosmeticCss) ".ad" else null,
        active = active,
    )
}

private fun org.json.JSONArray.strings(): List<String> =
    (0 until length()).map(::getString)

private fun org.json.JSONArray.ids(): List<String> =
    (0 until length()).map { index -> getJSONObject(index).getString("id") }
