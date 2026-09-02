package dev.sk2andy.materialbrowser.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncMutationCodecTest {
    @Test
    fun `all public tab mutations round trip canonically`() {
        val tab = SyncTab("tab-1", 0, 0, null, true, false, "Candy", "https://example.com/")
        val mutations = listOf(
            SyncPendingMutation.Open("open-1", "target-1", tab),
            SyncPendingMutation.Navigate(
                "navigate-1",
                "target-1",
                "tab-1",
                "Next",
                "https://example.com/next",
            ),
            SyncPendingMutation.Close("close-1", "target-1", "tab-1"),
            SyncPendingMutation.Reorder("reorder-1", "target-1", listOf("tab-2", "tab-1")),
            SyncPendingMutation.SetPinned("pin-1", "target-1", "tab-1", true),
        )

        mutations.forEach { mutation ->
            assertEquals(mutation, SyncMutationCodec.decode(SyncMutationCodec.encode(mutation)))
        }
    }

    @Test
    fun `private open and unknown fields fail closed`() {
        val privateOpen = SyncPendingMutation.Open(
            "open-1",
            "target-1",
            SyncTab("tab-1", 0, 0, null, true, false, "Private", "https://example.com/"),
            isPrivate = true,
        )
        assertThrows(IllegalArgumentException::class.java) { SyncMutationCodec.encode(privateOpen) }
        assertThrows(IllegalArgumentException::class.java) {
            SyncMutationCodec.decode(
                """{"schemaVersion":2,"mutationId":"close-1","targetDeviceId":"target-1","type":"close","candyId":"tab-1","extra":true}""",
            )
        }
    }

    @Test
    fun `canonical JSON matches JavaScript escaping and property order`() {
        val mutation = SyncPendingMutation.Navigate(
            mutationId = "navigate-1",
            targetDeviceId = "target-1",
            candyId = "tab-1",
            title = "Candy/\n\"🍬",
            url = "https://example.com/path",
        )

        assertEquals(
            "{\"schemaVersion\":2,\"mutationId\":\"navigate-1\",\"targetDeviceId\":\"target-1\"," +
                "\"type\":\"navigate\",\"candyId\":\"tab-1\",\"title\":\"Candy/\\n\\\"🍬\"," +
                "\"url\":\"https://example.com/path\"}",
            SyncMutationCodec.encode(mutation),
        )
    }
}
