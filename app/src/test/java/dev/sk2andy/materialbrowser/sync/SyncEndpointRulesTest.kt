package dev.sk2andy.materialbrowser.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncEndpointRulesTest {
    @Test
    fun `normalizes secure root endpoints`() {
        assertEquals("https://sync.example/", SyncEndpointRules.normalize(" https://sync.example "))
        assertEquals("http://localhost:8080/", SyncEndpointRules.normalize("http://localhost:8080"))
        assertEquals("http://[::1]:8080/", SyncEndpointRules.normalize("http://[::1]:8080/"))
    }

    @Test
    fun `rejects remote cleartext credentials paths and fragments`() {
        assertNull(SyncEndpointRules.normalize("http://sync.example/"))
        assertNull(SyncEndpointRules.normalize("https://user:secret@sync.example/"))
        assertNull(SyncEndpointRules.normalize("https://sync.example/v1"))
        assertNull(SyncEndpointRules.normalize("https://sync.example/#token"))
    }
}
