package dev.sk2andy.materialbrowser.blocking

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenericCosmeticRuntimeTest {
    @Test
    fun `payload separates and prefix compresses simple selectors`() {
        val payload = GenericCosmeticPayload.create(
            listOf(".advert", ".advert-box", "#ad-slot", "div[data-ad]"),
        )
        val fields = payload.encoded.split('.')

        assertEquals(3, fields.size)
        assertEquals("0:advert\n6:-box", decode(fields[0]))
        assertEquals("0:ad-slot", decode(fields[1]))
        assertEquals("div[data-ad]", decode(fields[2]))
        assertEquals(4, payload.selectorCount)
        assertEquals(3, payload.simpleSelectorCount)
        assertEquals(1, payload.complexSelectorCount)
    }

    @Test
    fun `policy encoding is deterministic and fail closed is explicit`() {
        val encoded = GenericCosmeticPolicyEncoding.encode(
            GenericCosmeticPolicy(
                disabled = false,
                deniedSelectors = listOf(".second", ".first", ".first"),
            ),
        )

        assertEquals(".first\n.second", decode(encoded))
        assertEquals(
            "!",
            GenericCosmeticPolicyEncoding.encode(GenericCosmeticPolicy(disabled = true)),
        )
    }

    @Test
    fun `document start runtime is token driven bounded and idempotent`() {
        val script = GenericCosmeticScript.create(
            pausedHosts = listOf("paused.example"),
            bridgeToken = BRIDGE_TOKEN,
        )

        assertTrue(script.contains("self.__candyGenericCosmeticV2"))
        assertTrue(script.contains("cache.classes.has(name)"))
        assertTrue(script.contains("cache.ids.has(id)"))
        assertTrue(script.contains("pendingSelectors.join(',')"))
        assertTrue(script.contains("maxSelectors=1024"))
        assertTrue(script.contains("performance.now()-started<4"))
        assertTrue(script.contains("maxPendingNodes=8192"))
        assertTrue(script.contains("'paused.example'"))
        assertTrue(script.contains("bridge.policy('$BRIDGE_TOKEN',host)"))
        assertTrue(script.contains("bridge.payload('$BRIDGE_TOKEN')"))
        assertTrue(script.length < 16_000)
    }

    @Test
    fun `host policy cache resolves once and evicts to its hard bound`() {
        val resolutions = mutableMapOf<String, Int>()
        val cache = GenericCosmeticPolicyCache(maxEntries = 2) { host ->
            resolutions[host] = resolutions.getOrDefault(host, 0) + 1
            "policy:$host"
        }

        assertEquals("policy:a.example", cache.get("a.example"))
        assertEquals("policy:a.example", cache.get("a.example"))
        cache.get("b.example")
        cache.get("c.example")

        assertEquals(1, resolutions["a.example"])
        assertEquals(2, cache.sizeForTesting)
    }

    private fun decode(value: String): String = String(
        Base64.getDecoder().decode(value),
        Charsets.UTF_8,
    )

    private companion object {
        const val BRIDGE_TOKEN = "01234567-89ab-cdef-0123-456789abcdef"
    }
}
