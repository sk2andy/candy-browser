package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebContentAnimationPolicyScriptTest {
    @Test
    fun `disabled animations suppress css web animations and smooth scrolling`() {
        val script = WebContentAnimationPolicyScript.script(animationsEnabled = false)

        assertTrue(script.contains("animation-duration: 0.001ms !important"))
        assertTrue(script.contains("transition-duration: 0s !important"))
        assertTrue(script.contains("scroll-behavior: auto !important"))
        assertTrue(script.contains("document.getAnimations()"))
        assertTrue(script.contains("animation.finish()"))
        assertTrue(script.contains("behavior: \"auto\""))
    }

    @Test
    fun `enabled animations emit only cleanup`() {
        val script = WebContentAnimationPolicyScript.script(animationsEnabled = true)

        assertTrue(script.contains("__candyAnimationPolicyCleanup"))
        assertFalse(script.contains("animation-duration"))
        assertFalse(script.contains("getAnimations"))
    }
}
