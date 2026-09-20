package dev.sk2andy.materialbrowser.browser.gecko

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeckoInlineVideoOpenRequestGateTest {
    @Test
    fun `open from acknowledged revision waits for current ack and fresh candidate`() {
        val gate = readyGate()
        gate.publish(policy.copy(topInsetPx = 0), revision = 4, isPrivate = false)

        assertNull(gate.accept(request, revision = 3, mode = MODE))
        assertNull(gate.takeReady())
        gate.acknowledge(4)
        assertNull(gate.takeReady())
        gate.updateCandidate(revision = 4, state = candidate)

        assertEquals(request, gate.takeReady())
        assertNull(gate.takeReady())
    }

    @Test
    fun `fresh current candidate may arrive before current ack`() {
        val gate = readyGate()
        gate.publish(policy.copy(topInsetPx = 0), revision = 4, isPrivate = false)
        assertNull(gate.accept(request, revision = 3, mode = MODE))
        gate.updateCandidate(revision = 4, state = candidate)
        assertNull(gate.takeReady())

        gate.acknowledge(4)

        assertEquals(request, gate.takeReady())
        assertNull(gate.takeReady())
    }

    @Test
    fun `current acknowledged open keeps immediate delivery`() {
        val gate = readyGate()

        assertEquals(request, gate.accept(request, revision = 3, mode = MODE))
    }

    @Test
    fun `only exact last acknowledged revision may wait`() {
        listOf(0L, 2L, 4L, 5L).forEach { revision ->
            val gate = readyGate()
            gate.publish(policy, revision = 4, isPrivate = false)
            assertNull(gate.accept(request, revision, MODE))
            gate.acknowledge(4)
            gate.updateCandidate(4, candidate)
            assertNull(gate.takeReady())
        }
    }

    @Test
    fun `changed policy cancels pending open including a later return to original mode`() {
        listOf(
            policy.copy(navigationGeneration = 3),
            policy.copy(inlineMediaPlayerEnabled = false),
            policy.copy(inlineMediaPlayerMode = "automatic"),
        ).forEach { changed ->
            val gate = readyGate()
            gate.publish(policy, revision = 4, isPrivate = false)
            gate.accept(request, revision = 3, mode = MODE)
            gate.invalidatePolicy(changed, isPrivate = false)
            assertNull(gate.pendingDeadlineMillis)
            gate.publish(changed, revision = 5, isPrivate = false)
            gate.publish(policy, revision = 6, isPrivate = false)
            assertNull(gate.accept(request, revision = 3, mode = MODE))
            gate.acknowledge(6)
            gate.updateCandidate(6, candidate)
            assertNull(gate.takeReady())
        }
    }

    @Test
    fun `private policy explicit close and binding cancellation clear pending open`() {
        listOf<(GeckoInlineVideoOpenRequestGate) -> Unit>(
            { it.invalidatePolicy(policy, isPrivate = true) },
            { it.accept(request.copy(expected = false), revision = 3, mode = null) },
            { it.cancel() },
        ).forEach { cancel ->
            val gate = readyGate()
            gate.publish(policy, revision = 4, isPrivate = false)
            gate.accept(request, revision = 3, mode = MODE)
            cancel(gate)
            gate.acknowledge(4)
            gate.updateCandidate(4, candidate)
            assertNull(gate.takeReady())
        }
    }

    @Test
    fun `inactive or replaced current candidate permanently cancels pending open`() {
        listOf(
            candidate.copy(isActive = false),
            candidate.copy(elementNonce = "c".repeat(32)),
            candidate.copy(documentNonce = "c".repeat(32)),
        ).forEach { changed ->
            val gate = readyGate()
            gate.publish(policy, revision = 4, isPrivate = false)
            gate.accept(request, revision = 3, mode = MODE)
            gate.updateCandidate(4, changed)
            gate.updateCandidate(4, candidate)
            gate.acknowledge(4)
            assertNull(gate.takeReady())
        }
    }

    @Test
    fun `duplicates do not extend fixed deadline or replace pending identity`() {
        var now = 100L
        val gate = readyGate { now }
        gate.publish(policy, revision = 4, isPrivate = false)
        gate.accept(request, revision = 3, mode = MODE)
        assertEquals(3_100L, gate.pendingDeadlineMillis)
        now = 2_000
        gate.accept(request, revision = 3, mode = MODE)
        gate.accept(request.copy(identity = identity.copy(elementNonce = "c".repeat(32))), 3, MODE)
        assertEquals(3_100L, gate.pendingDeadlineMillis)
        now = 3_100
        gate.acknowledge(4)
        gate.updateCandidate(4, candidate)
        assertNull(gate.takeReady())
        assertNull(gate.pendingDeadlineMillis)
    }

    @Test
    fun `old candidate report cannot satisfy current revision requirement`() {
        val gate = readyGate()
        gate.publish(policy, revision = 4, isPrivate = false)
        gate.accept(request, revision = 3, mode = MODE)
        gate.acknowledge(4)
        gate.updateCandidate(3, candidate)
        assertNull(gate.takeReady())
        gate.updateCandidate(4, candidate)
        assertEquals(request, gate.takeReady())
    }

    @Test
    fun `mode mismatch cannot enter pending slot`() {
        val gate = readyGate()
        gate.publish(policy, revision = 4, isPrivate = false)
        gate.accept(request, revision = 3, mode = "automatic")
        gate.acknowledge(4)
        gate.updateCandidate(4, candidate)
        assertNull(gate.takeReady())
    }

    private fun readyGate(nowMillis: () -> Long = { 0 }) =
        GeckoInlineVideoOpenRequestGate(nowMillis).apply {
            publish(policy, revision = 3, isPrivate = false)
            acknowledge(3)
            updateCandidate(revision = 3, state = candidate)
        }

    private companion object {
        const val MODE = "button_fullscreen"
        val policy = GeckoPrivacyPolicy.Disabled.copy(
            inlineMediaPlayerEnabled = true,
            inlineMediaPlayerMode = MODE,
            navigationGeneration = 2,
            topInsetPx = 24,
        )
        val identity = GeckoInlineVideoIdentity("a".repeat(32), "b".repeat(32))
        val request = GeckoInlineVideoOpenRequest(identity, navigationGeneration = 2)
        val candidate = GeckoInlineVideoState(
            isActive = true,
            isPlaying = true,
            isPresented = false,
            width = 1280,
            height = 720,
            documentNonce = identity.documentNonce,
            elementNonce = identity.elementNonce,
        )
    }
}
