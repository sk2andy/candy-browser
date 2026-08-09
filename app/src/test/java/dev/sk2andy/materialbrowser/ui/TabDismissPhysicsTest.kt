package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabDismissPhysicsTest {

    @Test
    fun signedVisualDistance_preservesBothDismissDirections() {
        assertEquals(-22f, TabDismissPhysics.signedVisualDistance(-40f), 0.001f)
        assertEquals(22f, TabDismissPhysics.signedVisualDistance(40f), 0.001f)
        assertEquals(0f, TabDismissPhysics.signedVisualDistance(0f), 0.001f)
    }

    @Test
    fun `configured first forty percent uses resistance`() {
        assertEquals(0f, TabDismissPhysics.visualDistance(0f), 0.001f)
        assertEquals(11f, TabDismissPhysics.visualDistance(20f), 0.001f)
        assertEquals(21.45f, TabDismissPhysics.visualDistance(39f), 0.001f)
    }

    @Test
    fun `release animation moves card smoothly to finger`() {
        assertEquals(22f, TabDismissPhysics.visualDistance(40f, 0f), 0.001f)
        assertEquals(31f, TabDismissPhysics.visualDistance(40f, 0.5f), 0.001f)
        assertEquals(40f, TabDismissPhysics.visualDistance(40f, 1f), 0.001f)
        assertEquals(40.9f, TabDismissPhysics.visualDistance(40f, 1.05f), 0.001f)
    }

    @Test
    fun `release closes at configured resistance end`() {
        assertFalse(TabDismissPhysics.hasClearedResistance(39.999f, 100f))
        assertTrue(TabDismissPhysics.hasClearedResistance(40f, 100f))
    }

    @Test
    fun `dragging back below resistance end cancels close`() {
        assertTrue(TabDismissPhysics.hasClearedResistance(40f, 100f))
        assertFalse(TabDismissPhysics.hasClearedResistance(39f, 100f))
    }

    @Test
    fun `custom resistance fraction changes resisted range`() {
        assertFalse(TabDismissPhysics.hasClearedResistance(69f, 100f, 0.7f))
        assertTrue(TabDismissPhysics.hasClearedResistance(70f, 100f, 0.7f))
    }

    @Test
    fun `continuous haptic is limited to resistance phase`() {
        assertFalse(TabDismissPhysics.isInResistancePhase(1f, 0f))
        assertFalse(TabDismissPhysics.isInResistancePhase(0f, 100f))
        assertTrue(TabDismissPhysics.isInResistancePhase(1f, 100f))
        assertTrue(TabDismissPhysics.isInResistancePhase(39.999f, 100f))
        assertFalse(TabDismissPhysics.isInResistancePhase(40f, 100f))
    }

    @Test
    fun `resistance fraction remains within settings limits`() {
        assertFalse(TabDismissPhysics.hasClearedResistance(9f, 100f, 0f))
        assertTrue(TabDismissPhysics.hasClearedResistance(10f, 100f, 0f))
        assertFalse(TabDismissPhysics.hasClearedResistance(89f, 100f, 1f))
        assertTrue(TabDismissPhysics.hasClearedResistance(90f, 100f, 1f))
    }

}
