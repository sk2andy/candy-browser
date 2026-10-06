package dev.sk2andy.materialbrowser.browser.gecko

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoSessionPriorityControllerTest {
    @Test
    fun `selected tab stays protected independently of visibility`() {
        val fixture = Fixture()
        fixture.controller.setSelected(true)
        fixture.controller.setSelected(true)
        assertEquals(listOf(true), fixture.priorities)
        assertEquals(0, fixture.formChecks.size)
        assertEquals(0, fixture.expiries.size)
    }

    @Test
    fun `previous tab without form data returns to default immediately`() {
        val fixture = Fixture()
        fixture.controller.setSelected(true)
        fixture.controller.setSelected(false)
        fixture.formChecks.single()(false)
        assertEquals(listOf(true, false), fixture.priorities)
        assertTrue(fixture.expiries.single().cancelled)
    }

    @Test
    fun `form data and unknown form state receive bounded protection`() {
        listOf(true, null).forEach { formData ->
            val fixture = Fixture()
            fixture.controller.setSelected(true)
            fixture.controller.setSelected(false)
            fixture.formChecks.single()(formData)
            assertFalse(fixture.expiries.single().cancelled)
            fixture.expiries.single().run()
            assertEquals(listOf(true, true, false), fixture.priorities)
            // A late callback cannot restore high priority after expiry.
            fixture.formChecks.single()(formData)
            assertEquals(false, fixture.priorities.last())
        }
        assertEquals(180_000L, GeckoSessionPriorityController.FORM_PRIORITY_LIFETIME_MILLIS)
    }

    @Test
    fun `missing form response cannot protect previous tab indefinitely`() {
        val fixture = Fixture()
        fixture.controller.setSelected(true)
        fixture.controller.setSelected(false)
        fixture.expiries.single().run()
        assertEquals(listOf(true, false), fixture.priorities)
    }

    @Test
    fun `reselection rejects stale form result and expiry`() {
        val fixture = Fixture()
        fixture.controller.setSelected(true)
        fixture.controller.setSelected(false)
        fixture.controller.setSelected(true)
        fixture.formChecks.single()(false)
        fixture.expiries.single().run()
        assertEquals(listOf(true, true), fixture.priorities)
        assertTrue(fixture.expiries.single().cancelled)
    }

    @Test
    fun `navigation change discards protection based on previous document`() {
        val fixture = Fixture()
        fixture.controller.setSelected(true)
        fixture.controller.setSelected(false)
        fixture.navigation++
        fixture.formChecks.single()(true)
        assertEquals(listOf(true, false), fixture.priorities)
        assertTrue(fixture.expiries.single().cancelled)
    }

    @Test
    fun `closed session cancels expiry and rejects callbacks`() {
        val fixture = Fixture()
        fixture.controller.setSelected(true)
        fixture.controller.setSelected(false)
        fixture.controller.close()
        fixture.formChecks.single()(false)
        fixture.expiries.single().run()
        fixture.controller.setSelected(true)
        assertEquals(listOf(true), fixture.priorities)
        assertTrue(fixture.expiries.single().cancelled)
    }

    private class Fixture {
        var navigation = 1L
        val priorities = mutableListOf<Boolean>()
        val formChecks = mutableListOf<(Boolean?) -> Unit>()
        val expiries = mutableListOf<Expiry>()
        val controller = GeckoSessionPriorityController(
            containsFormData = { formChecks += it },
            navigationGeneration = { navigation },
            setHighPriority = { priorities += it },
            scheduleExpiry = { action ->
                val expiry = Expiry(action)
                expiries += expiry
                val cancel: () -> Unit = { expiry.cancelled = true }
                cancel
            },
        )
    }

    private class Expiry(private val action: () -> Unit) {
        var cancelled = false
        fun run() = action()
    }
}
