package dev.sk2andy.materialbrowser.browser.gecko.webpush

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import dev.sk2andy.materialbrowser.data.writeSafely
import java.io.File
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

internal data class FossWebPushState(
    val uaid: String = "",
    val subscriptions: List<FossPushSubscription> = emptyList(),
    val pendingUnregister: List<String> = emptyList(),
    val deliveredVersions: Map<String, List<String>> = emptyMap(),
)

/** Push identity and decryption keys stay in encrypted, non-backed-up app storage. */
internal class FossWebPushStore(context: Context) {
    private val file = AtomicFile(File(context.applicationContext.noBackupFilesDir, FILE_NAME))

    @Synchronized
    fun load(): FossWebPushState = runCatching {
        val encrypted = file.openRead().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8_192)
            var size = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                size += read
                require(size <= MAX_FILE_BYTES)
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
        val plaintext = decrypt(encrypted)
        try {
            decode(plaintext)
        } finally {
            plaintext.fill(0)
        }
    }.getOrDefault(FossWebPushState())

    @Synchronized
    fun save(state: FossWebPushState): Boolean = runCatching {
        val plaintext = encode(state)
        try {
            require(plaintext.size <= MAX_FILE_BYTES)
            file.writeSafely { output -> output.write(encrypt(plaintext)) }
        } finally {
            plaintext.fill(0)
        }
    }.getOrDefault(false)

    @Synchronized
    fun clear(): Boolean = runCatching {
        file.delete()
        if (file.baseFile.exists()) return@runCatching false
        runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(KEY_ALIAS)
        }
        true
    }.getOrDefault(false)

    private fun encode(state: FossWebPushState): ByteArray {
        require(state.subscriptions.size <= MAX_SUBSCRIPTIONS)
        val subscriptions = JSONArray()
        state.subscriptions.forEach { subscription ->
            require(FossWebPushScopeRules.isPersistentScope(subscription.scope))
            require(FossWebPushScopeRules.isSecureEndpoint(subscription.endpoint))
            subscriptions.put(
                JSONObject()
                    .put("scope", subscription.scope)
                    .put("endpoint", subscription.endpoint)
                    .put("channelId", subscription.channelId)
                    .put("appServerKey", subscription.appServerKey?.let(::base64))
                    .put("publicKey", base64(subscription.keys.publicKey))
                    .put("privateKeyPkcs8", base64(subscription.keys.privateKeyPkcs8))
                    .put("authSecret", base64(subscription.keys.authSecret)),
            )
        }
        return JSONObject()
            .put("version", 1)
            .put("uaid", state.uaid)
            .put("subscriptions", subscriptions)
            .put("pendingUnregister", JSONArray(state.pendingUnregister))
            .put(
                "deliveredVersions",
                JSONObject().also { versions ->
                    state.deliveredVersions.forEach { (channelId, recent) ->
                        require(isUuid(channelId) && recent.size <= MAX_RECENT_VERSIONS)
                        require(recent.all { it.length in 1..MAX_VERSION_LENGTH })
                        versions.put(channelId, JSONArray(recent))
                    }
                },
            )
            .toString()
            .toByteArray(Charsets.UTF_8)
    }

    private fun decode(plaintext: ByteArray): FossWebPushState {
        require(plaintext.size <= MAX_FILE_BYTES)
        val value = JSONObject(plaintext.toString(Charsets.UTF_8))
        require(value.getInt("version") == 1)
        val uaid = value.getString("uaid").also { require(it.isEmpty() || UAID_PATTERN.matches(it)) }
        val items = value.getJSONArray("subscriptions")
        require(items.length() <= MAX_SUBSCRIPTIONS)
        val subscriptions = (0 until items.length()).map { index ->
            val item = items.getJSONObject(index)
            val scope = item.getString("scope")
            val endpoint = item.getString("endpoint")
            val channelId = item.getString("channelId")
            require(FossWebPushScopeRules.isPersistentScope(scope))
            require(FossWebPushScopeRules.isSecureEndpoint(endpoint))
            require(isUuid(channelId))
            FossPushSubscription(
                scope = scope,
                endpoint = endpoint,
                channelId = channelId,
                appServerKey = if (item.isNull("appServerKey")) null else decodeBase64(item.getString("appServerKey"), 65),
                keys = WebPushKeyMaterial(
                    publicKey = decodeBase64(item.getString("publicKey"), 65),
                    privateKeyPkcs8 = decodeBase64(item.getString("privateKeyPkcs8"), 256),
                    authSecret = decodeBase64(item.getString("authSecret"), 16),
                ),
            )
        }
        require(subscriptions.map(FossPushSubscription::scope).toSet().size == subscriptions.size)
        require(subscriptions.map(FossPushSubscription::channelId).toSet().size == subscriptions.size)
        val pending = value.getJSONArray("pendingUnregister")
        require(pending.length() <= MAX_PENDING_UNREGISTER)
        val pendingUnregister = (0 until pending.length()).map { index ->
            pending.getString(index).also { require(isUuid(it)) }
        }
        val delivered = value.optJSONObject("deliveredVersions") ?: JSONObject()
        require(delivered.length() <= MAX_SUBSCRIPTIONS)
        val activeChannelIds = subscriptions.map(FossPushSubscription::channelId).toSet()
        val deliveredVersions = delivered.keys().asSequence()
            .filter { it in activeChannelIds }
            .associateWith { channelId ->
                val recent = delivered.getJSONArray(channelId)
                require(recent.length() <= MAX_RECENT_VERSIONS)
                (0 until recent.length()).map { index ->
                    recent.getString(index).also { require(it.length in 1..MAX_VERSION_LENGTH) }
                }.distinct()
            }
        return FossWebPushState(uaid, subscriptions, pendingUnregister.distinct(), deliveredVersions)
    }

    private fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(AAD)
        return JSONObject()
            .put("nonce", base64(cipher.iv))
            .put("ciphertext", base64(cipher.doFinal(plaintext)))
            .toString()
            .toByteArray(Charsets.UTF_8)
    }

    private fun decrypt(envelope: ByteArray): ByteArray {
        val value = JSONObject(envelope.toString(Charsets.UTF_8))
        val nonce = decodeBase64(value.getString("nonce"), 12)
        val ciphertext = decodeBase64(value.getString("ciphertext"), MAX_FILE_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, nonce))
        cipher.updateAAD(AAD)
        return cipher.doFinal(ciphertext)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun base64(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun decodeBase64(value: String, maximum: Int): ByteArray {
        require(value.length <= maximum * 2)
        return Base64.getUrlDecoder().decode(value).also { require(it.size <= maximum) }
    }

    private fun isUuid(value: String): Boolean = UUID_PATTERN.matches(value)

    private companion object {
        const val FILE_NAME = "candy_web_push_foss_v1"
        const val KEY_ALIAS = "candy_web_push_foss_v1"
        const val MAX_SUBSCRIPTIONS = 256
        const val MAX_PENDING_UNREGISTER = 512
        const val MAX_RECENT_VERSIONS = 10
        const val MAX_VERSION_LENGTH = 256
        const val MAX_FILE_BYTES = 2 * 1_024 * 1_024
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        val AAD = "candy-web-push/foss/v1".toByteArray()
        val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        val UAID_PATTERN = Regex("[0-9a-fA-F-]{1,128}")
    }
}
