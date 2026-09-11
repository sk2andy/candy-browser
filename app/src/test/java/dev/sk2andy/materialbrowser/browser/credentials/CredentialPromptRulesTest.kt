package dev.sk2andy.materialbrowser.browser.credentials

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialPromptRulesTest {
    @Test
    fun `regular active https page creates origin-bound identity`() {
        val identity = CredentialPromptRules.identity(
            tabId = "tab-1",
            profileId = "profile-1",
            isPrivate = false,
            isActive = true,
            pageUrl = "https://Example.com:8443/login?next=%2F",
            sessionGeneration = 7,
            navigationGeneration = 11,
        )

        assertEquals("https://example.com:8443", identity?.origin)
        assertEquals("profile-1", identity?.profileId)
    }

    @Test
    fun `private inactive and insecure pages cannot create credential prompt identity`() {
        val base = arrayOf(false, true)
        base.forEach { privateMode ->
            assertNull(
                CredentialPromptRules.identity(
                    tabId = "tab",
                    profileId = "profile",
                    isPrivate = privateMode,
                    isActive = !privateMode,
                    pageUrl = if (privateMode) "https://example.com" else "http://example.com",
                    sessionGeneration = 1,
                    navigationGeneration = 1,
                ),
            )
        }
    }

    @Test
    fun `explicit opt in creates exact http login selection identity`() {
        val identity = CredentialPromptRules.identity(
            tabId = "tab-1",
            profileId = "profile-1",
            isPrivate = false,
            isActive = true,
            pageUrl = "http://Example.com:8080/login?next=%2F",
            sessionGeneration = 7,
            navigationGeneration = 11,
            allowHttp = true,
        )

        assertEquals("http://example.com:8080", identity?.origin)
        assertNull(
            CredentialPromptRules.identity(
                tabId = "tab-1",
                profileId = "profile-1",
                isPrivate = false,
                isActive = true,
                pageUrl = "ftp://example.com/login",
                sessionGeneration = 7,
                navigationGeneration = 11,
                allowHttp = true,
            ),
        )
    }

    @Test
    fun `http opt in never admits private or inactive pages`() {
        listOf(
            true to true,
            false to false,
        ).forEach { (privateMode, active) ->
            assertNull(
                CredentialPromptRules.identity(
                    tabId = "tab",
                    profileId = "profile",
                    isPrivate = privateMode,
                    isActive = active,
                    pageUrl = "http://example.com/login",
                    sessionGeneration = 1,
                    navigationGeneration = 1,
                    allowHttp = true,
                ),
            )
        }
    }

    @Test
    fun `login accepts bounded values and never prints secrets`() {
        val login = CredentialPromptRules.login("alice", "secret")

        assertNotNull(login)
        assertEquals("alice", login?.username)
        assertFalse(login.toString().contains("alice"))
        assertFalse(login.toString().contains("secret"))
        assertNull(CredentialPromptRules.login("alice", ""))
        assertNull(CredentialPromptRules.login("alice", "x".repeat(4_097)))
    }

    @Test
    fun `login origin must match exact web origin`() {
        assertTrue(
            CredentialPromptRules.matchesOrigin(
                candidate = "https://example.com/login",
                expected = "https://example.com",
            ),
        )
        assertFalse(
            CredentialPromptRules.matchesOrigin(
                candidate = "https://accounts.example.com",
                expected = "https://example.com",
            ),
        )
        assertFalse(
            CredentialPromptRules.matchesOrigin(
                candidate = "http://example.com",
                expected = "https://example.com",
            ),
        )
        assertTrue(
            CredentialPromptRules.matchesOrigin(
                candidate = "http://example.com/login",
                expected = "http://example.com",
            ),
        )
    }

    @Test
    fun `selected login stays inside offered usernames when options exist`() {
        val allowed = CredentialPromptRules.allowedUserIds(listOf("alice", "bob"))

        assertEquals(setOf("alice", "bob"), allowed)
        assertTrue(CredentialPromptRules.acceptsSelectedUser("alice", requireNotNull(allowed)))
        assertFalse(CredentialPromptRules.acceptsSelectedUser("mallory", allowed))
        assertTrue(CredentialPromptRules.acceptsSelectedUser("mallory", emptySet()))
    }

    @Test
    fun `identity provider and account options reject duplicate ids and unsafe labels`() {
        assertNull(
            CredentialPromptRules.providers(
                listOf(
                    IdentityCredentialProvider(1, "One", "id.example"),
                    IdentityCredentialProvider(1, "Two", "login.example"),
                ),
            ),
        )
        assertNull(
            CredentialPromptRules.accounts(
                listOf(IdentityCredentialAccount(1, "Alice\u202e", "alice@example.com")),
            ),
        )
        assertEquals(
            "id.example",
            CredentialPromptRules.providers(
                listOf(IdentityCredentialProvider(2, "Provider", "ID.Example.")),
            )?.single()?.domain,
        )
    }

    @Test
    fun `identity privacy policy binds relying party and provider domain`() {
        val identity = requireNotNull(
            CredentialPromptRules.identity(
                tabId = "tab",
                profileId = "profile",
                isPrivate = false,
                isActive = true,
                pageUrl = "https://shop.example/path",
                sessionGeneration = 1,
                navigationGeneration = 1,
            ),
        )

        assertNotNull(
            CredentialPromptRules.identityPrivacyPrompt(
                identity = identity,
                providerName = "Identity Provider",
                providerDomain = "id.example",
                relyingPartyHost = "shop.example",
                privacyPolicyUrl = "https://legal.id.example/privacy",
                termsOfServiceUrl = "https://id.example/terms",
            ),
        )
        assertNull(
            CredentialPromptRules.identityPrivacyPrompt(
                identity = identity,
                providerName = "Identity Provider",
                providerDomain = "id.example",
                relyingPartyHost = "other.example",
                privacyPolicyUrl = "https://id.example/privacy",
                termsOfServiceUrl = null,
            ),
        )
        assertNull(
            CredentialPromptRules.identityPrivacyPrompt(
                identity = identity,
                providerName = "Identity Provider",
                providerDomain = "id.example",
                relyingPartyHost = "shop.example",
                privacyPolicyUrl = "https://id.example.evil.test/privacy",
                termsOfServiceUrl = null,
            ),
        )
    }

    @Test
    fun `stale navigation identity is rejected`() {
        val captured = CredentialPromptIdentity("tab", "profile", "https://example.com", 2, 3)

        assertTrue(CredentialPromptRules.remainsCurrent(captured, captured.copy()))
        assertFalse(
            CredentialPromptRules.remainsCurrent(
                captured,
                captured.copy(navigationGeneration = 4),
            ),
        )
    }
}
