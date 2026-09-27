package dev.sk2andy.materialbrowser.browser.gecko.webpush

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Per-subscription secrets. Callers must keep privateKeyPkcs8 and authSecret out of private-mode storage. */
internal data class WebPushKeyMaterial(
    val publicKey: ByteArray,
    val privateKeyPkcs8: ByteArray,
    val authSecret: ByteArray,
)

/** RFC 8291 Web Push decryption over the single-record RFC 8188 aes128gcm body. */
internal object WebPushCrypto {
    private const val PUBLIC_KEY_BYTES = 65
    private const val AUTH_SECRET_BYTES = 16
    private const val HEADER_BYTES = 16 + 4 + 1 + PUBLIC_KEY_BYTES
    private const val GCM_TAG_BYTES = 16
    private const val MAX_BODY_BYTES = 65_536

    fun generateKeyMaterial(): WebPushKeyMaterial {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
        val pair = generator.generateKeyPair()
        val publicKey = pair.public as java.security.interfaces.ECPublicKey
        val authSecret = ByteArray(AUTH_SECRET_BYTES).also(SecureRandom()::nextBytes)
        return WebPushKeyMaterial(
            publicKey = byteArrayOf(4) +
                publicKey.w.affineX.toFixedWidthBytes(32) +
                publicKey.w.affineY.toFixedWidthBytes(32),
            privateKeyPkcs8 = pair.private.encoded,
            authSecret = authSecret,
        )
    }

    /** Returns null for invalid encoding, invalid keys, failed authentication, or invalid padding. */
    fun decryptAes128Gcm(body: ByteArray, keys: WebPushKeyMaterial): ByteArray? {
        if (body.size !in (HEADER_BYTES + GCM_TAG_BYTES + 1)..MAX_BODY_BYTES) return null
        if (keys.authSecret.size != AUTH_SECRET_BYTES || keys.privateKeyPkcs8.size !in 1..512) return null
        if (body[20].toInt() and 0xff != PUBLIC_KEY_BYTES) return null

        val recordSize = ByteBuffer.wrap(body, 16, 4).int.toLong() and 0xffff_ffffL
        val recordBytes = body.size - HEADER_BYTES
        if (recordSize < 18 || recordBytes > recordSize) return null

        return runCatching {
            val parameters = ecParameters()
            val senderPublic = body.copyOfRange(21, HEADER_BYTES)
            val senderKey = decodePublicKey(senderPublic, parameters) ?: return null
            if (decodePublicKey(keys.publicKey, parameters) == null) return null
            val receiverPrivate = KeyFactory.getInstance("EC")
                .generatePrivate(PKCS8EncodedKeySpec(keys.privateKeyPkcs8))
            val agreement = KeyAgreement.getInstance("ECDH")
            agreement.init(receiverPrivate)
            agreement.doPhase(senderKey, true)
            val sharedSecret = agreement.generateSecret()
            val authPrk = hmac(keys.authSecret, sharedSecret)
            val keyInfo = "WebPush: info".toByteArray(StandardCharsets.US_ASCII) +
                byteArrayOf(0) + keys.publicKey + senderPublic + byteArrayOf(1)
            val inputKeyMaterial = hmac(authPrk, keyInfo)
            val salt = body.copyOfRange(0, 16)
            val prk = hmac(salt, inputKeyMaterial)
            val contentKey = hmac(
                prk,
                "Content-Encoding: aes128gcm".toByteArray(StandardCharsets.US_ASCII) + byteArrayOf(0, 1),
            ).copyOf(16)
            val nonce = hmac(
                prk,
                "Content-Encoding: nonce".toByteArray(StandardCharsets.US_ASCII) + byteArrayOf(0, 1),
            ).copyOf(12)

            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(contentKey, "AES"), GCMParameterSpec(128, nonce))
                val padded = cipher.doFinal(body, HEADER_BYTES, recordBytes)
                var delimiterIndex = padded.lastIndex
                while (delimiterIndex >= 0 && padded[delimiterIndex] == 0.toByte()) delimiterIndex--
                if (delimiterIndex < 0 || padded[delimiterIndex] != 2.toByte()) return null
                padded.copyOfRange(0, delimiterIndex)
            } finally {
                sharedSecret.fill(0)
                authPrk.fill(0)
                inputKeyMaterial.fill(0)
                prk.fill(0)
                contentKey.fill(0)
                nonce.fill(0)
            }
        }.getOrNull()
    }

    private fun ecParameters(): ECParameterSpec {
        val parameters = AlgorithmParameters.getInstance("EC")
        parameters.init(ECGenParameterSpec("secp256r1"))
        return parameters.getParameterSpec(ECParameterSpec::class.java)
    }

    private fun decodePublicKey(encoded: ByteArray, parameters: ECParameterSpec): java.security.PublicKey? {
        if (encoded.size != PUBLIC_KEY_BYTES || encoded[0] != 4.toByte()) return null
        val x = BigInteger(1, encoded.copyOfRange(1, 33))
        val y = BigInteger(1, encoded.copyOfRange(33, PUBLIC_KEY_BYTES))
        val field = parameters.curve.field as java.security.spec.ECFieldFp
        val prime = field.p
        if (x >= prime || y >= prime) return null
        val left = y.modPow(BigInteger.valueOf(2), prime)
        val right = x.modPow(BigInteger.valueOf(3), prime)
            .add(parameters.curve.a.multiply(x))
            .add(parameters.curve.b)
            .mod(prime)
        if (left != right) return null
        return KeyFactory.getInstance("EC")
            .generatePublic(ECPublicKeySpec(ECPoint(x, y), parameters))
    }

    private fun hmac(key: ByteArray, value: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(value)
        }

    private fun BigInteger.toFixedWidthBytes(width: Int): ByteArray {
        val encoded = toByteArray()
        require(encoded.size <= width + 1)
        return encoded.copyOfRange((encoded.size - width).coerceAtLeast(0), encoded.size)
            .let { ByteArray(width - it.size) + it }
    }
}
