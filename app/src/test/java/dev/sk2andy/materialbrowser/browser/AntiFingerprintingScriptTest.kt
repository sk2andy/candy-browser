package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AntiFingerprintingScriptTest {
    @Test
    fun `script standardizes high entropy surfaces and partitions canvas noise`() {
        val script = AntiFingerprintingScript.create("0123456789abcdef")

        assertTrue(script.contains("hardwareConcurrency"))
        assertTrue(script.contains("deviceMemory"))
        assertTrue(script.contains("platformVersion"))
        assertTrue(script.contains("location?.hostname"))
        assertTrue(script.contains("getImageData"))
        assertTrue(script.contains("toDataURL"))
        assertTrue(script.contains("ANGLE (Google, Vulkan)"))
        assertFalse(script.contains("Pixel 9"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `script rejects unsafe seed interpolation`() {
        AntiFingerprintingScript.create("');alert(1);//")
    }
}
