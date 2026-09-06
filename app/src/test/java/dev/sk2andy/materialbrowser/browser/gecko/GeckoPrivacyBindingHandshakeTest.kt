package dev.sk2andy.materialbrowser.browser.gecko

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoPrivacyBindingHandshakeTest {
    @Test
    fun `older bound revision waits for current policy acknowledgement`() {
        var state = GeckoPrivacyBindingHandshakeRules.publish(GeckoPrivacyBindingHandshake())
        val firstAck = GeckoPrivacyBindingHandshakeRules.acknowledgePolicy(state, revision = 1)
        state = firstAck.state
        assertTrue(firstAck.startBootstrap)

        state = GeckoPrivacyBindingHandshakeRules.publish(state)
        val authenticated = GeckoPrivacyBindingHandshakeRules.authenticateSession(
            state,
            revision = 1,
        )
        state = authenticated.state
        assertTrue(authenticated.accepted)
        val staleBound = GeckoPrivacyBindingHandshakeRules.bindSession(state, revision = 1)
        state = staleBound.state
        assertTrue(staleBound.accepted)
        assertFalse(state.isReady)

        val currentAck = GeckoPrivacyBindingHandshakeRules.acknowledgePolicy(state, revision = 2)
        assertTrue(currentAck.state.isReady)
        assertFalse(currentAck.startBootstrap)
    }

    @Test
    fun `policy updates before first acknowledgement start exactly one bootstrap`() {
        var state = GeckoPrivacyBindingHandshakeRules.publish(GeckoPrivacyBindingHandshake())
        state = GeckoPrivacyBindingHandshakeRules.publish(state)

        val firstAck = GeckoPrivacyBindingHandshakeRules.acknowledgePolicy(state, revision = 1)
        state = firstAck.state
        assertTrue(firstAck.startBootstrap)
        assertFalse(state.isCurrentPolicyAcknowledged)

        val currentAck = GeckoPrivacyBindingHandshakeRules.acknowledgePolicy(state, revision = 2)
        assertTrue(currentAck.accepted)
        assertFalse(currentAck.startBootstrap)
        assertTrue(currentAck.state.isCurrentPolicyAcknowledged)
    }

    @Test
    fun `unknown revision and binding before bootstrap remain rejected`() {
        val published = GeckoPrivacyBindingHandshakeRules.publish(GeckoPrivacyBindingHandshake())

        assertFalse(
            GeckoPrivacyBindingHandshakeRules.authenticateSession(published, revision = 1).accepted,
        )
        assertFalse(
            GeckoPrivacyBindingHandshakeRules.acknowledgePolicy(published, revision = 0).accepted,
        )
        assertFalse(
            GeckoPrivacyBindingHandshakeRules.acknowledgePolicy(published, revision = 2).accepted,
        )
    }

    @Test
    fun `global binding confirmation requires prior session authentication and is single use`() {
        var state = GeckoPrivacyBindingHandshakeRules.publish(GeckoPrivacyBindingHandshake())
        state = GeckoPrivacyBindingHandshakeRules.acknowledgePolicy(state, revision = 1).state

        assertFalse(GeckoPrivacyBindingHandshakeRules.bindSession(state, revision = 1).accepted)
        state = GeckoPrivacyBindingHandshakeRules.authenticateSession(state, revision = 1).state
        val confirmed = GeckoPrivacyBindingHandshakeRules.bindSession(state, revision = 1)

        assertTrue(confirmed.accepted)
        assertTrue(confirmed.state.sessionBound)
        assertFalse(
            GeckoPrivacyBindingHandshakeRules.bindSession(
                confirmed.state,
                revision = 1,
            ).accepted,
        )
    }

    @Test
    fun `fresh binding generation does not inherit authenticated revisions`() {
        var previous = GeckoPrivacyBindingHandshakeRules.publish(GeckoPrivacyBindingHandshake())
        previous = GeckoPrivacyBindingHandshakeRules.acknowledgePolicy(previous, revision = 1).state
        previous = GeckoPrivacyBindingHandshakeRules.authenticateSession(previous, revision = 1).state

        val rebound = GeckoPrivacyBindingHandshakeRules.publish(GeckoPrivacyBindingHandshake())

        assertFalse(GeckoPrivacyBindingHandshakeRules.bindSession(rebound, revision = 1).accepted)
        assertFalse(rebound.sessionBound)
        assertTrue(previous.authenticatedBindingRevisions.contains(1))
    }
}
