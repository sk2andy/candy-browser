package dev.sk2andy.materialbrowser.data.sync

import dev.sk2andy.materialbrowser.sync.SyncEncryptedChange
import dev.sk2andy.materialbrowser.sync.SyncEncryptedDelta
import java.io.BufferedInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncHttpClientTest {
    @Test
    fun `remote HTTP never sends credentials before discovery approval`() {
        val client = SyncHttpClient("http://sync.example/")
        val error = runCatching {
            client.bootstrap("candy", "password".toByteArray())
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message?.contains("before credentials") == true)
    }

    @Test
    fun `discovery and bootstrap use bounded strict protocol and Basic auth`() {
        TestServer(
            responses = listOf(
                """{"protocol":"candy-sync","versions":[1],"allowHttp":false,"features":["e2ee","tab-snapshots","encrypted-device-icons"],"limits":{"batchChanges":100,"payloadBytes":1048576,"devices":1000}}""",
                """{"protocolVersion":1,"cryptoVersion":1,"workspaceId":"workspace_1","serverEpoch":"epoch_1","initialized":false,"kdf":{"algorithm":"argon2id-v1","salt":"AAAAAAAAAAAAAAAAAAAAAA","memoryKiB":65536,"iterations":3,"parallelism":4,"keyBytes":32},"recoveryEnvelope":null}""",
            ),
        ).use { server ->
            val client = SyncHttpClient(server.endpoint)
            client.discover()
            val password = "password".toByteArray()
            assertEquals("workspace_1", client.bootstrap("candy", password).workspaceId)
            password.fill(0)

            server.awaitRequests(2)
            assertEquals("GET /.well-known/candy-sync HTTP/1.1", server.requests[0].requestLine)
            assertEquals(
                "Basic ${Base64.getEncoder().encodeToString("candy:password".toByteArray())}",
                server.requests[1].headers["authorization"],
            )
        }
    }

    @Test
    fun `cross-device snapshot sends bearer idempotency and exact CAS body`() {
        TestServer(listOf("""{"revision":"8","cursor":"epoch.8"}""")).use { server ->
            val response = SyncHttpClient(server.endpoint).putTabs(
                "token",
                SyncEncryptedChange(
                    changeId = "attempt-123",
                    writerDeviceId = "android-device",
                    targetDeviceId = "target-device",
                    baseRevision = 7,
                    revision = 8,
                    nonce = "AAECAwQFBgcICQoL",
                    ciphertext = "AAAAAAAAAAAAAAAAAAAAAA",
                ),
            )
            assertEquals(8, response.revision)
            assertEquals("epoch.8", response.cursor)
            server.awaitRequests(1)
            val request = server.requests.single()
            assertEquals("PUT /v1/devices/target-device/tabs HTTP/1.1", request.requestLine)
            assertEquals("Bearer token", request.headers["authorization"])
            assertEquals("attempt-123", request.headers["idempotency-key"])
            val body = JSONObject(request.body)
            assertEquals("attempt-123", body.getString("changeId"))
            assertEquals("7", body.getString("expectedRevision"))
            assertEquals("8", body.getString("revision"))
            assertTrue(!body.has("deviceId") && !body.has("entityId"))
        }
    }

    @Test
    fun `v2 discovery enables encrypted delta push with tenant metadata`() {
        TestServer(
            listOf(
                """{"protocol":"candy-sync","versions":[1,2],"allowHttp":false,"features":["e2ee","tab-snapshots","encrypted-device-icons","tab-mutations-v2","realtime"],"limits":{"batchChanges":100,"payloadBytes":1048576,"devices":1000}}""",
                """{"cursor":"epoch.8","results":[{"changeId":"attempt-123","revision":"8"}]}""",
            ),
        ).use { server ->
            val client = SyncHttpClient(server.endpoint)
            client.discover()
            assertTrue(client.supportsTabMutationsV2())
            assertTrue(client.supportsRealtime())
            val response = client.pushDelta(
                "token",
                SyncEncryptedDelta(
                    changeId = "attempt-123",
                    mutationId = "logical-123",
                    workspaceId = "workspace-1",
                    writerDeviceId = "android-device",
                    targetDeviceId = "target-device",
                    baseRevision = 7,
                    revision = null,
                    nonce = "AAECAwQFBgcICQoL",
                    ciphertext = "AAAAAAAAAAAAAAAAAAAAAA",
                ),
            )

            assertEquals(8, response.revision)
            server.awaitRequests(2)
            val request = server.requests[1]
            assertEquals("POST /v2/sync/push HTTP/1.1", request.requestLine)
            assertEquals("attempt-123", request.headers["idempotency-key"])
            val change = JSONObject(request.body).getJSONArray("changes").getJSONObject(0)
            assertEquals("logical-123", change.getString("mutationId"))
            assertEquals("workspace-1", change.getString("workspaceId"))
            assertEquals("android-device", change.getString("deviceId"))
            assertEquals("delta", change.getString("operation"))
            assertTrue(!change.has("revision"))
        }
    }

    private data class RecordedRequest(
        val requestLine: String,
        val headers: Map<String, String>,
        val body: String,
    )

    private class TestServer(
        private val responses: List<String>,
    ) : AutoCloseable {
        private val socket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        private val thread = Thread(::serve, "sync-http-test").apply {
            isDaemon = true
            start()
        }
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        val endpoint = "http://127.0.0.1:${socket.localPort}/"

        fun awaitRequests(count: Int) {
            repeat(100) {
                if (requests.size >= count) return
                Thread.sleep(10)
            }
            error("Timed out waiting for requests")
        }

        override fun close() {
            socket.close()
            thread.join(1_000)
        }

        private fun serve() {
            responses.forEach { body ->
                val client = runCatching { socket.accept() }.getOrNull() ?: return
                client.use {
                    val input = BufferedInputStream(it.getInputStream())
                    val requestLine = input.readHttpLine()
                    val headers = buildMap {
                        while (true) {
                            val line = input.readHttpLine()
                            if (line.isEmpty()) break
                            put(line.substringBefore(':').lowercase(), line.substringAfter(':').trim())
                        }
                    }
                    val length = headers["content-length"]?.toInt() ?: 0
                    val requestBody = ByteArray(length)
                    var offset = 0
                    while (offset < length) {
                        val read = input.read(requestBody, offset, length - offset)
                        check(read >= 0)
                        offset += read
                    }
                    requests += RecordedRequest(requestLine, headers, requestBody.toString(StandardCharsets.UTF_8))
                    val bytes = body.toByteArray(StandardCharsets.UTF_8)
                    val response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                    it.getOutputStream().use { output ->
                        output.write(response.toByteArray(StandardCharsets.US_ASCII))
                        output.write(bytes)
                    }
                }
            }
        }

        private fun BufferedInputStream.readHttpLine(): String {
            val bytes = mutableListOf<Byte>()
            while (true) {
                val next = read()
                check(next >= 0)
                if (next == '\n'.code) break
                if (next != '\r'.code) bytes += next.toByte()
                check(bytes.size <= 16_384)
            }
            return bytes.toByteArray().toString(StandardCharsets.US_ASCII)
        }
    }
}
