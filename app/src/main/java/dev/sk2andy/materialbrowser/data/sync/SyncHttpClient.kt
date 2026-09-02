package dev.sk2andy.materialbrowser.data.sync

import dev.sk2andy.materialbrowser.sync.SyncBootstrap
import dev.sk2andy.materialbrowser.sync.SyncDeviceIdentity
import dev.sk2andy.materialbrowser.sync.SyncDeviceRecord
import dev.sk2andy.materialbrowser.sync.SyncEncryptedChange
import dev.sk2andy.materialbrowser.sync.SyncEncryptedValue
import dev.sk2andy.materialbrowser.sync.SyncEndpointRules
import dev.sk2andy.materialbrowser.sync.SyncProtocolCodec
import dev.sk2andy.materialbrowser.sync.SyncPullPage
import dev.sk2andy.materialbrowser.sync.SyncRecoveryEnvelope
import dev.sk2andy.materialbrowser.sync.SyncServerSnapshot
import dev.sk2andy.materialbrowser.sync.parseStrictJsonObject
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.json.JSONObject

data class SyncEnrollmentResponse(
    val workspaceId: String,
    val deviceId: String,
    val token: String,
    val cursor: String,
)

data class SyncPutResponse(
    val revision: Long,
    val cursor: String,
)

class SyncTransportException(
    val statusCode: Int?,
    val problemCode: String?,
    cause: Throwable? = null,
) : Exception("Candy Sync transport failed", cause)

interface SyncTransport {
    fun discover()
    fun bootstrap(username: String, password: ByteArray): SyncBootstrap
    fun enroll(
        username: String,
        password: ByteArray,
        identity: SyncDeviceIdentity,
        encryptedName: SyncEncryptedValue,
        encryptedIcon: SyncEncryptedValue,
        recoveryEnvelope: SyncRecoveryEnvelope?,
    ): SyncEnrollmentResponse
    fun listDevices(token: String): List<SyncDeviceRecord>
    fun pull(token: String, cursor: String): SyncPullPage
    fun snapshot(token: String): SyncServerSnapshot
    fun putTabs(token: String, change: SyncEncryptedChange): SyncPutResponse
    fun acknowledge(token: String, cursor: String)
}

class SyncHttpClient(endpoint: String) : SyncTransport {
    private val endpoint = URI(requireNotNull(SyncEndpointRules.normalize(endpoint)))

    override fun discover() {
        val value = parseStrictJsonObject(request(".well-known/candy-sync", "GET"))
        require(value.keys().asSequence().toSet() == setOf("protocol", "versions", "features", "limits"))
        require(value.strictString("protocol", 32) == "candy-sync")
        val versions = value.getJSONArray("versions")
        require(versions.length() in 1..16)
        require((0 until versions.length()).all { versions.get(it) is Int })
        require((0 until versions.length()).any { versions.get(it) == 1 })
        val features = value.getJSONArray("features")
        require(features.length() in 1..32)
        val supported = (0 until features.length()).map { index ->
            features.get(index) as? String ?: throw IllegalArgumentException("Invalid feature")
        }.toSet()
        require(supported.containsAll(setOf("e2ee", "tab-snapshots", "encrypted-device-icons")))
        val limits = value.getJSONObject("limits")
        require(limits.keys().asSequence().toSet() == setOf("batchChanges", "payloadBytes", "devices"))
        require(limits.strictInt("batchChanges") in 1..1_000)
        require(limits.strictInt("payloadBytes") in 1_024..MAX_RESPONSE_BYTES)
        require(limits.strictInt("devices") in 1..10_000)
    }

    override fun bootstrap(username: String, password: ByteArray): SyncBootstrap =
        SyncProtocolCodec.decodeBootstrap(
            request("v1/bootstrap", "GET", authorization = basic(username, password)),
        )

    override fun enroll(
        username: String,
        password: ByteArray,
        identity: SyncDeviceIdentity,
        encryptedName: SyncEncryptedValue,
        encryptedIcon: SyncEncryptedValue,
        recoveryEnvelope: SyncRecoveryEnvelope?,
    ): SyncEnrollmentResponse {
        val value = parseStrictJsonObject(
            request(
                path = "v1/devices",
                method = "POST",
                authorization = basic(username, password),
                body = SyncProtocolCodec.encodeEnrollment(
                    identity,
                    encryptedName,
                    encryptedIcon,
                    recoveryEnvelope,
                ),
            ),
        )
        val allowed = setOf("workspaceId", "deviceId", "token", "cursor", "expiresAt")
        require(value.keys().asSequence().all { it in allowed })
        require(value.has("workspaceId") && value.has("deviceId") && value.has("token") && value.has("cursor"))
        return SyncEnrollmentResponse(
            workspaceId = value.identifier("workspaceId"),
            deviceId = value.identifier("deviceId"),
            token = value.strictString("token", 512).also { require(it.none(Char::isWhitespace)) },
            cursor = value.strictString("cursor", 260).also(SyncProtocolCodec::requireCursor),
        )
    }

    override fun listDevices(token: String): List<SyncDeviceRecord> =
        SyncProtocolCodec.decodeDevices(request("v1/devices", "GET", bearer(token)))

    override fun pull(token: String, cursor: String): SyncPullPage = SyncProtocolCodec.decodePull(
        request(
            path = "v1/sync/pull?after=${java.net.URLEncoder.encode(cursor, StandardCharsets.UTF_8)}&limit=100",
            method = "GET",
            authorization = bearer(token),
        ),
    )

    override fun snapshot(token: String): SyncServerSnapshot = SyncProtocolCodec.decodeServerSnapshot(
        request("v1/sync/snapshot", "GET", bearer(token)),
    )

    override fun putTabs(token: String, change: SyncEncryptedChange): SyncPutResponse {
        val value = parseStrictJsonObject(
            request(
                path = "v1/devices/${change.targetDeviceId}/tabs",
                method = "PUT",
                authorization = bearer(token),
                body = SyncProtocolCodec.encodePutSnapshot(change),
                idempotencyKey = change.changeId,
            ),
        )
        require(value.keys().asSequence().toSet() == setOf("revision", "cursor"))
        return SyncPutResponse(
            revision = value.strictRevision("revision"),
            cursor = value.strictString("cursor", 260).also(SyncProtocolCodec::requireCursor),
        )
    }

    override fun acknowledge(token: String, cursor: String) {
        request(
            path = "v1/sync/ack",
            method = "POST",
            authorization = bearer(token),
            body = JSONObject().put("cursor", cursor).toString(),
            expectBody = false,
        )
    }

    private fun request(
        path: String,
        method: String,
        authorization: String? = null,
        body: String? = null,
        idempotencyKey: String? = null,
        expectBody: Boolean = true,
    ): String {
        var connection: HttpURLConnection? = null
        try {
            connection = endpoint.resolve(path).toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.requestMethod = method
            connection.setRequestProperty("Accept", "application/json")
            authorization?.let { connection.setRequestProperty("Authorization", it) }
            idempotencyKey?.let { connection.setRequestProperty("Idempotency-Key", it) }
            if (body != null) {
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                require(bytes.size <= MAX_REQUEST_BYTES)
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(bytes) }
                bytes.fill(0)
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                val error = connection.errorStream?.use { it.readBytesLimited(MAX_ERROR_BYTES) }
                val code = error?.let(::problemCode)
                throw SyncTransportException(status, code)
            }
            if (!expectBody) return ""
            return connection.inputStream.use { stream ->
                stream.readBytesLimited(MAX_RESPONSE_BYTES).decodeUtf8()
            }
        } catch (error: SyncTransportException) {
            throw error
        } catch (error: Exception) {
            throw SyncTransportException(null, null, error)
        } finally {
            connection?.disconnect()
        }
    }

    private fun basic(username: String, password: ByteArray): String {
        require(username.isNotBlank() && username.length <= 128 && ':' !in username)
        val usernameBytes = username.toByteArray(StandardCharsets.UTF_8)
        val credentials = ByteArray(usernameBytes.size + 1 + password.size)
        usernameBytes.copyInto(credentials)
        credentials[usernameBytes.size] = ':'.code.toByte()
        password.copyInto(credentials, usernameBytes.size + 1)
        return try {
            "Basic ${Base64.getEncoder().encodeToString(credentials)}"
        } finally {
            credentials.fill(0)
            usernameBytes.fill(0)
        }
    }

    private fun bearer(token: String): String {
        require(token.isNotEmpty() && token.length <= 512 && token.none(Char::isWhitespace))
        return "Bearer $token"
    }

    private fun problemCode(raw: ByteArray): String? = runCatching {
        parseStrictJsonObject(raw.decodeUtf8()).optString("code").takeIf(String::isNotEmpty)
    }.getOrNull()

    private fun java.io.InputStream.readBytesLimited(maximum: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > maximum) throw SyncTransportException(null, "response_too_large")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun JSONObject.strictString(name: String, maximum: Int): String = (get(name) as? String).also {
        requireNotNull(it)
    }!!.also {
        require(it.isNotEmpty() && it.length <= maximum)
    }

    private fun JSONObject.strictInt(name: String): Int = get(name) as? Int
        ?: throw IllegalArgumentException("Invalid $name")

    private fun JSONObject.strictRevision(name: String): Long {
        val value = strictString(name, 19)
        require(value == "0" || value.first() in '1'..'9')
        return value.toLongOrNull()?.also { require(it >= 0) }
            ?: throw IllegalArgumentException("Invalid $name")
    }

    private fun JSONObject.identifier(name: String): String = strictString(name, 128).also {
        require(it.matches(IDENTIFIER))
    }

    private fun ByteArray.decodeUtf8(): String = StandardCharsets.UTF_8.newDecoder()
        .decode(java.nio.ByteBuffer.wrap(this))
        .toString()

    private companion object {
        const val TIMEOUT_MILLIS = 10_000
        const val MAX_REQUEST_BYTES = 1_048_576
        const val MAX_RESPONSE_BYTES = 1_048_576
        const val MAX_ERROR_BYTES = 16_384
        val IDENTIFIER = Regex("[A-Za-z0-9_-]+")
    }
}
