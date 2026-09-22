package dev.sk2andy.materialbrowser.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class CandyMotionDurationScaleTest {
    @Test
    fun `animations default to real time and can be disabled live`() {
        val scale = CandyMotionDurationScale(animationsEnabled = true)

        assertEquals(1f, scale.scaleFactor)

        scale.updateAnimationsEnabled(false)

        assertEquals(0f, scale.scaleFactor)
    }
}
