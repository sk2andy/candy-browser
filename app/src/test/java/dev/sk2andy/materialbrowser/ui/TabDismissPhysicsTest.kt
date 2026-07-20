package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabDismissPhysicsTest {
    @Test
    fun `first forty percent uses resistance`() {
        assertEquals(0f, TabDismissPhysics.visualDistance(0f, 100f), 0.001f)
        assertEquals(11f, TabDismissPhysics.visualDistance(20f, 100f), 0.001f)
        assertEquals(22f, TabDismissPhysics.visualDistance(40f, 100f), 0.001f)
    }

    @Test
    fun `remaining distance returns to linear movement`() {
        assertEquals(32f, TabDismissPhysics.visualDistance(50f, 100f), 0.001f)
        assertEquals(82f, TabDismissPhysics.visualDistance(100f, 100f), 0.001f)
        assertEquals(100f, TabDismissPhysics.visualDistance(118f, 100f), 0.001f)
    }

    @Test
    fun `resistance vibration is limited to first configured band`() {
        assertFalse(TabDismissPhysics.isInResistanceBand(0f, 100f))
        assertTrue(TabDismissPhysics.isInResistanceBand(1f, 100f))
        assertTrue(TabDismissPhysics.isInResistanceBand(39f, 100f))
        assertFalse(TabDismissPhysics.isInResistanceBand(40f, 100f))
    }

    @Test
    fun `dismiss happens only after resisted band is completed`() {
        assertFalse(TabDismissPhysics.isDismissed(100f, 100f))
        assertTrue(TabDismissPhysics.isDismissed(118f, 100f))
    }

    @Test
    fun `custom resistance fraction changes resisted range`() {
        assertEquals(38.5f, TabDismissPhysics.visualDistance(70f, 100f, 0.7f), 0.001f)
        assertEquals(48.5f, TabDismissPhysics.visualDistance(80f, 100f, 0.7f), 0.001f)
    }
}
