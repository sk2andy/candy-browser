package dev.sk2andy.materialbrowser.sync

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import com.lambdapioneer.argon2kt.Argon2Version
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

fun interface SyncRecoveryKeyDeriver {
    fun derive(passphrase: ByteArray, kdf: SyncRecoveryKdf): ByteArray
}

class AndroidArgon2RecoveryKeyDeriver : SyncRecoveryKeyDeriver {
    override fun derive(passphrase: ByteArray, kdf: SyncRecoveryKdf): ByteArray {
        require(kdf.memoryKiB == 65_536 && kdf.iterations == 3 && kdf.parallelism == 4)
        val salt = SyncBase64.decode(kdf.salt, expectedBytes = 16)
        val passwordBuffer = ByteBuffer.allocateDirect(passphrase.size).put(passphrase)
        val saltBuffer = ByteBuffer.allocateDirect(salt.size).put(salt)
        return try {
            val result = Argon2Kt().hash(
                mode = Argon2Mode.ARGON2_ID,
                password = passwordBuffer,
                salt = saltBuffer,
                tCostInIterations = kdf.iterations,
                mCostInKibibyte = kdf.memoryKiB,
                parallelism = kdf.parallelism,
                hashLengthInBytes = 32,
                version = Argon2Version.V13,
            )
            try {
                result.rawHashAsByteArray()
            } finally {
                result.rawHash.clearBuffer()
                result.encodedOutput.clearBuffer()
            }
        } finally {
            passwordBuffer.clearBuffer()
            saltBuffer.clearBuffer()
            salt.fill(0)
        }
    }
}

class SyncCrypto(
    private val random: SecureRandom = SecureRandom(),
) {
    fun generateDeviceIdentity(): SyncDeviceIdentity {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"), random)
        val pair = generator.generateKeyPair()
        val publicKey = pair.public.encoded
        return SyncDeviceIdentity(
            privateKeyPkcs8 = pair.private.encoded,
            publicKeySpki = publicKey,
            fingerprint = fingerprint(publicKey),
        )
    }

    fun fingerprint(publicKeySpki: ByteArray): String =
        SyncBase64.encode(MessageDigest.getInstance("SHA-256").digest(publicKeySpki))

    fun createRecoveryEnvelope(
        recoveryKey: ByteArray,
        workspaceKey: ByteArray,
        workspaceId: String,
    ): SyncRecoveryEnvelope {
        require(recoveryKey.size == 32 && workspaceKey.size == 32)
        return encryptAes(
            key = recoveryKey,
            plaintext = workspaceKey,
            aad = "candy-sync/recovery-envelope/v1/$workspaceId".utf8(),
        ).let { SyncRecoveryEnvelope(nonce = it.nonce, ciphertext = it.ciphertext) }
    }

    fun unlockRecoveryEnvelope(
        recoveryKey: ByteArray,
        envelope: SyncRecoveryEnvelope,
        workspaceId: String,
    ): ByteArray {
        require(envelope.cryptoVersion == 1)
        return decryptAes(
            key = recoveryKey,
            encrypted = SyncEncryptedValue(envelope.nonce, envelope.ciphertext),
            aad = "candy-sync/recovery-envelope/v1/$workspaceId".utf8(),
            maxCiphertextBytes = 48,
        ).also { require(it.size == 32) }
    }

    fun encryptDeviceName(
        workspaceKey: ByteArray,
        workspaceId: String,
        fingerprint: String,
        name: String,
    ): SyncEncryptedValue {
        require(name.isNotEmpty() && name.length <= 80)
        val key = deriveDeviceKey(workspaceKey, workspaceId, "device-name", fingerprint)
        val plaintext = name.utf8()
        return try {
            encryptAes(
                key = key,
                plaintext = plaintext,
                aad = jsonArray("candy-sync-device-name", 1, workspaceId, fingerprint).utf8(),
            )
        } finally {
            key.fill(0)
            plaintext.fill(0)
        }
    }

    fun decryptDeviceName(
        workspaceKey: ByteArray,
        workspaceId: String,
        fingerprint: String,
        encrypted: SyncEncryptedValue,
    ): String {
        val key = deriveDeviceKey(workspaceKey, workspaceId, "device-name", fingerprint)
        val plaintext = try {
            decryptAes(
                key = key,
                encrypted = encrypted,
                aad = jsonArray("candy-sync-device-name", 1, workspaceId, fingerprint).utf8(),
                maxCiphertextBytes = 4_096,
            )
        } finally {
            key.fill(0)
        }
        return try {
            plaintext.decodeUtf8().also { require(it.isNotEmpty() && it.length <= 80) }
        } finally {
            plaintext.fill(0)
        }
    }

    fun encryptDeviceIcon(
        workspaceKey: ByteArray,
        workspaceId: String,
        fingerprint: String,
        descriptor: SyncDeviceIconDescriptor,
    ): SyncEncryptedValue {
        SyncDeviceIconRules.requireValid(descriptor)
        val plaintext = JSONObject()
            .put("schemaVersion", 1)
            .put("catalogId", descriptor.catalogId)
            .put("accentHue", descriptor.accentHue)
            .toString()
            .utf8()
        val key = deriveDeviceKey(workspaceKey, workspaceId, "device-icon", fingerprint)
        return try {
            encryptAes(
                key = key,
                plaintext = plaintext,
                aad = jsonArray("candy-sync-device-icon", 1, workspaceId, fingerprint).utf8(),
            )
        } finally {
            key.fill(0)
            plaintext.fill(0)
        }
    }

    fun decryptDeviceIcon(
        workspaceKey: ByteArray,
        workspaceId: String,
        fingerprint: String,
        encrypted: SyncEncryptedValue,
    ): SyncDeviceIconDescriptor {
        val key = deriveDeviceKey(workspaceKey, workspaceId, "device-icon", fingerprint)
        val plaintext = try {
            decryptAes(
                key = key,
                encrypted = encrypted,
                aad = jsonArray("candy-sync-device-icon", 1, workspaceId, fingerprint).utf8(),
                maxCiphertextBytes = 4_096,
            )
        } finally {
            key.fill(0)
        }
        return try {
            SyncProtocolCodec.decodeDeviceIcon(plaintext.decodeUtf8())
        } finally {
            plaintext.fill(0)
        }
    }

    fun encryptTabSnapshot(
        workspaceKey: ByteArray,
        metadata: SyncEncryptedChange,
        snapshot: SyncTabSnapshot,
    ): SyncEncryptedChange {
        require(metadata.revision == null)
        val safeSnapshot = requireNotNull(SyncTabRules.normalizeSnapshot(snapshot))
        val plaintext = SyncProtocolCodec.encodeTabSnapshot(safeSnapshot).utf8()
        return try {
            val key = derivePayloadKey(workspaceKey, metadata.targetDeviceId)
            val encrypted = try {
                encryptAes(key = key, plaintext = plaintext, aad = changeAad(metadata))
            } finally {
                key.fill(0)
            }
            metadata.copy(nonce = encrypted.nonce, ciphertext = encrypted.ciphertext)
        } finally {
            plaintext.fill(0)
        }
    }

    fun decryptTabSnapshot(
        workspaceKey: ByteArray,
        change: SyncEncryptedChange,
    ): SyncTabSnapshot {
        val key = derivePayloadKey(workspaceKey, change.targetDeviceId)
        val plaintext = try {
            decryptAes(
                key = key,
                encrypted = SyncEncryptedValue(change.nonce, change.ciphertext),
                aad = changeAad(change),
                maxCiphertextBytes = 393_216,
            )
        } finally {
            key.fill(0)
        }
        return try {
            SyncProtocolCodec.decodeTabSnapshot(plaintext.decodeUtf8())
        } finally {
            plaintext.fill(0)
        }
    }

    private fun deriveDeviceKey(
        workspaceKey: ByteArray,
        workspaceId: String,
        purpose: String,
        fingerprint: String,
    ): ByteArray {
        SyncBase64.decode(fingerprint, expectedBytes = 32)
        return hkdf(
            inputKey = workspaceKey,
            salt = workspaceId.utf8(),
            info = "candy-sync/v1/$purpose/$fingerprint".utf8(),
        )
    }

    private fun derivePayloadKey(workspaceKey: ByteArray, targetDeviceId: String): ByteArray = hkdf(
        inputKey = workspaceKey,
        salt = targetDeviceId.utf8(),
        info = "candy-sync/v1/payload/tabs".utf8(),
    )

    private fun changeAad(change: SyncEncryptedChange): ByteArray = jsonArray(
        "candy-sync-change",
        1,
        1,
        1,
        change.writerDeviceId,
        change.changeId,
        "tabs",
        change.targetDeviceId,
        "snapshot",
        change.baseRevision.toString(),
    ).utf8()

    private fun encryptAes(key: ByteArray, plaintext: ByteArray, aad: ByteArray): SyncEncryptedValue {
        require(key.size == 32)
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad)
        return SyncEncryptedValue(
            nonce = SyncBase64.encode(nonce),
            ciphertext = SyncBase64.encode(cipher.doFinal(plaintext)),
        )
    }

    private fun decryptAes(
        key: ByteArray,
        encrypted: SyncEncryptedValue,
        aad: ByteArray,
        maxCiphertextBytes: Int,
    ): ByteArray {
        require(key.size == 32)
        val nonce = SyncBase64.decode(encrypted.nonce, expectedBytes = 12)
        val ciphertext = SyncBase64.decode(encrypted.ciphertext, maxBytes = maxCiphertextBytes)
        require(ciphertext.size >= 16)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    private fun hkdf(inputKey: ByteArray, salt: ByteArray, info: ByteArray): ByteArray {
        require(inputKey.size == 32)
        val extract = Mac.getInstance("HmacSHA256")
        extract.init(SecretKeySpec(salt, "HmacSHA256"))
        val pseudoRandomKey = extract.doFinal(inputKey)
        return try {
            val expand = Mac.getInstance("HmacSHA256")
            expand.init(SecretKeySpec(pseudoRandomKey, "HmacSHA256"))
            expand.update(info)
            expand.doFinal(byteArrayOf(1))
        } finally {
            pseudoRandomKey.fill(0)
        }
    }
}

object SyncBase64 {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()
    private val pattern = Regex("[A-Za-z0-9_-]+")

    fun encode(value: ByteArray): String = encoder.encodeToString(value)

    fun decode(value: String, expectedBytes: Int? = null, maxBytes: Int = 524_288): ByteArray {
        require(value.isNotEmpty() && value.length <= ((maxBytes * 4L + 2L) / 3L).toInt())
        require(pattern.matches(value) && !value.contains('='))
        val decoded = runCatching { decoder.decode(value) }.getOrElse { throw IllegalArgumentException("Invalid base64url") }
        require(decoded.size <= maxBytes)
        if (expectedBytes != null) require(decoded.size == expectedBytes)
        return decoded
    }
}

private fun String.utf8(): ByteArray = toByteArray(StandardCharsets.UTF_8)

private fun ByteArray.decodeUtf8(): String = StandardCharsets.UTF_8.newDecoder()
    .decode(ByteBuffer.wrap(this))
    .toString()

private fun ByteBuffer.clearBuffer() {
    clear()
    while (hasRemaining()) put(0)
    clear()
}

private fun jsonArray(vararg values: Any): String = values.joinToString(
    prefix = "[",
    postfix = "]",
    separator = ",",
) { value ->
    when (value) {
        is Number -> value.toString()
        is String -> JSONObject.quote(value)
        else -> error("Unsupported JSON value")
    }
}
