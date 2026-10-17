package dev.sk2andy.materialbrowser.browser.translation

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InlineTranslationCoordinatorInstrumentedTest {
    private val pair = InlineTranslationPair("en", "de")
    private val languages = InlineTranslationAvailability(listOf(pair), null)

    @Test
    fun unavailableServiceNeverInvokesFallbackAutomatically() = onMain {
        val backend = FakeBackend()
        val coordinator = InlineTranslationCoordinator(backend)
        var fallbackCount = 0
        coordinator.open(fallback = { fallbackCount++ })
        backend.availabilityCallbacks.single()(InlineTranslationAvailability(emptyList(), null))

        assertFalse(requireNotNull(coordinator.state).loading)
        assertTrue(requireNotNull(coordinator.state).pairs.isEmpty())
        assertFalse(requireNotNull(coordinator.state).canManageLanguages)
        coordinator.translate(pair)
        coordinator.manageLanguages()
        assertTrue(backend.translations.isEmpty())
        assertEquals(0, fallbackCount)
        coordinator.close()
    }

    @Test
    fun explicitFallbackRunsOnceAndClearsSelectedText() = onMain {
        val backend = FakeBackend()
        val coordinator = InlineTranslationCoordinator(backend)
        var fallbackCount = 0
        coordinator.open(fallback = { fallbackCount++ })

        coordinator.usePageProvider()
        coordinator.usePageProvider()
        backend.availabilityCallbacks.single()(languages)

        assertEquals(1, fallbackCount)
        assertNull(coordinator.state)
        assertTrue(backend.translations.isEmpty())
        coordinator.close()
    }

    @Test
    fun oldTranslationCannotApplyToNewSelectionOnSamePage() = onMain {
        val backend = FakeBackend()
        val coordinator = InlineTranslationCoordinator(backend)
        val applied = mutableListOf<String>()
        coordinator.open(text = "First selection", apply = { payload, _ -> applied += payload })
        backend.availabilityCallbacks[0](languages)
        coordinator.translate(pair)
        coordinator.open(text = "Second selection", apply = { payload, _ -> applied += payload })

        backend.translationCallbacks[0]("Erste Auswahl")
        backend.availabilityCallbacks[0](languages)

        assertTrue(applied.isEmpty())
        assertEquals("Second selection", coordinator.state?.text)
        assertTrue(requireNotNull(coordinator.state).loading)
        backend.availabilityCallbacks[1](languages)
        assertFalse(requireNotNull(coordinator.state).loading)
        coordinator.close()
    }

    @Test
    fun dismissalRejectsLateResultWithoutKeepingPrivateText() = onMain {
        val backend = FakeBackend()
        val coordinator = InlineTranslationCoordinator(backend)
        var applied = false
        coordinator.open(text = "Private selected text", apply = { _, _ -> applied = true })
        backend.availabilityCallbacks.single()(languages)
        coordinator.translate(pair)

        coordinator.dismiss()
        backend.translationCallbacks.single()("Privater ausgewählter Text")

        assertNull(coordinator.state)
        assertFalse(applied)
        assertTrue(backend.cancelCount > 0)
        coordinator.close()
    }

    @Test
    fun navigationInvalidatesTranslationAndFallback() = onMain {
        val backend = FakeBackend()
        val coordinator = InlineTranslationCoordinator(backend)
        var current = true
        var applied = false
        var fallback = false
        coordinator.open(
            isCurrent = { current },
            apply = { _, _ -> applied = true },
            fallback = { fallback = true },
        )
        backend.availabilityCallbacks.single()(languages)
        coordinator.translate(pair)
        current = false

        backend.translationCallbacks.single()("Hallo")
        coordinator.usePageProvider()

        assertNull(coordinator.state)
        assertFalse(applied)
        assertFalse(fallback)
        coordinator.close()
    }

    @Test
    fun oldPageApplyCompletionCannotDismissNewTranslation() = onMain {
        val backend = FakeBackend()
        val coordinator = InlineTranslationCoordinator(backend)
        var appliedPayload: JSONObject? = null
        var completion: ((Boolean) -> Unit)? = null
        coordinator.open(apply = { payload, callback ->
            appliedPayload = JSONObject(payload)
            completion = callback
        })
        backend.availabilityCallbacks.single()(languages)
        coordinator.translate(pair)
        backend.translationCallbacks.single()("Hallo")

        assertEquals("translate", appliedPayload?.getString("operation"))
        assertEquals("https://example.org/article", appliedPayload?.getString("url"))
        assertEquals("Hello", appliedPayload?.getString("original"))
        assertEquals("Hallo", appliedPayload?.getString("replacement"))
        coordinator.open(text = "Next selection")
        requireNotNull(completion)(true)

        assertEquals("Next selection", coordinator.state?.text)
        assertTrue(requireNotNull(coordinator.state).loading)
        coordinator.close()
    }

    @Test
    fun translationFailureStaysLocalAndClosePreventsReopening() = onMain {
        val backend = FakeBackend()
        val coordinator = InlineTranslationCoordinator(backend)
        var fallbackCount = 0
        coordinator.open(fallback = { fallbackCount++ })
        backend.availabilityCallbacks.single()(languages)
        coordinator.translate(pair)
        backend.translationCallbacks.single()(null)

        assertTrue(requireNotNull(coordinator.state).failed)
        assertFalse(requireNotNull(coordinator.state).translating)
        assertEquals(0, fallbackCount)
        coordinator.close()
        coordinator.open(text = "Must not reopen")
        coordinator.close()

        assertNull(coordinator.state)
        assertEquals(1, backend.closeCount)
        assertEquals(1, backend.availabilityCallbacks.size)
    }

    private fun InlineTranslationCoordinator.open(
        text: String = "Hello",
        isCurrent: () -> Boolean = { true },
        apply: (String, (Boolean) -> Unit) -> Unit = { _, _ -> },
        fallback: () -> Unit = {},
    ) = open(
        text = text,
        url = "https://example.org/article",
        toggleLabel = "Show original",
        isCurrent = isCurrent,
        apply = apply,
        fallback = fallback,
    )

    private fun onMain(action: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    }

    private class FakeBackend : InlineTranslationBackend {
        val availabilityCallbacks = mutableListOf<(InlineTranslationAvailability) -> Unit>()
        val translationCallbacks = mutableListOf<(String?) -> Unit>()
        val translations = mutableListOf<Pair<String, InlineTranslationPair>>()
        var cancelCount = 0
        var closeCount = 0

        override fun availability(onComplete: (InlineTranslationAvailability) -> Unit) {
            availabilityCallbacks += onComplete
        }

        override fun translate(text: String, pair: InlineTranslationPair, onComplete: (String?) -> Unit) {
            translations += text to pair
            translationCallbacks += onComplete
        }

        override fun cancel() {
            cancelCount++
        }

        override fun close() {
            closeCount++
        }
    }
}
