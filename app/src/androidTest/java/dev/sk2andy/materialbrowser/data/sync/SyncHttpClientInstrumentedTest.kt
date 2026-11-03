package dev.sk2andy.materialbrowser.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.sync.SyncRealtimeTicket
import java.util.concurrent.TimeUnit
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncHttpClientInstrumentedTest {
    @Test
    fun pullAndRealtimeUseAndroidCompatibleUrlEncoding() {
        MockWebServer().use { server ->
            val page = """{"changes":[],"nextCursor":"epoch.1","hasMore":false}"""
            server.enqueue(MockResponse().setBody(page))
            server.enqueue(MockResponse().setBody(page))
            server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {}))
            server.start()
            val client = SyncHttpClient(server.url("/").toString())

            client.pull("token", "epoch.1")
            client.pullDeltas("token", "epoch.1")
            assertEquals("/v1/sync/pull?after=epoch.1&limit=100", server.takeRequest(5, TimeUnit.SECONDS)?.path)
            assertEquals("/v2/sync/pull?after=epoch.1&limit=100", server.takeRequest(5, TimeUnit.SECONDS)?.path)

            client.connectRealtime(SyncRealtimeTicket("ticket", "2026-01-01T00:00:00Z"), {}, {}).use {
                assertEquals("/v2/realtime?ticket=ticket", server.takeRequest(5, TimeUnit.SECONDS)?.path)
            }
        }
    }
}
