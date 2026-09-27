package dev.sk2andy.materialbrowser.browser.gecko.webpush

import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPrivateKeySpec
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WebPushCryptoTest {
    @Test
    fun `decrypts the official RFC 8291 aes128gcm example`() {
        val plaintext = WebPushCrypto.decryptAes128Gcm(rfcBody(), rfcKeys())

        assertNotNull(plaintext)
        assertEquals("When I grow up, I want to be a watermelon", String(plaintext!!, Charsets.UTF_8))
    }

    @Test
    fun `accepts a final record exactly as long as declared record size`() {
        val body = rfcBody()
        val recordSize = body.size - 86
        body[16] = 0
        body[17] = 0
        body[18] = (recordSize ushr 8).toByte()
        body[19] = recordSize.toByte()

        val plaintext = WebPushCrypto.decryptAes128Gcm(body, rfcKeys())

        assertEquals("When I grow up, I want to be a watermelon", String(plaintext!!, Charsets.UTF_8))
    }

    @Test
    fun `generated subscription keys have expected public and auth formats`() {
        val keys = WebPushCrypto.generateKeyMaterial()
        val nextKeys = WebPushCrypto.generateKeyMaterial()

        assertEquals(65, keys.publicKey.size)
        assertEquals(4.toByte(), keys.publicKey[0])
        assertEquals(16, keys.authSecret.size)
        assertFalse(keys.privateKeyPkcs8.isEmpty())
        assertFalse(keys.publicKey.contentEquals(nextKeys.publicKey))
        assertFalse(keys.authSecret.contentEquals(nextKeys.authSecret))
    }

    @Test
    fun `tampered ciphertext and wrong auth secret fail authentication`() {
        val body = rfcBody()
        body[body.lastIndex] = (body.last().toInt() xor 1).toByte()
        assertNull(WebPushCrypto.decryptAes128Gcm(body, rfcKeys()))

        val keys = rfcKeys()
        keys.authSecret[0] = (keys.authSecret[0].toInt() xor 1).toByte()
        assertNull(WebPushCrypto.decryptAes128Gcm(rfcBody(), keys))
    }

    @Test
    fun `malformed headers and invalid sender point are rejected`() {
        val valid = rfcBody()
        assertNull(WebPushCrypto.decryptAes128Gcm(valid.copyOf(86), rfcKeys()))

        val wrongKeyLength = valid.clone()
        wrongKeyLength[20] = 64
        assertNull(WebPushCrypto.decryptAes128Gcm(wrongKeyLength, rfcKeys()))

        val invalidPoint = valid.clone()
        invalidPoint.fill(0, 21, 86)
        invalidPoint[21] = 4
        assertNull(WebPushCrypto.decryptAes128Gcm(invalidPoint, rfcKeys()))

        val multipleRecords = valid.clone()
        multipleRecords[16] = 0
        multipleRecords[17] = 0
        multipleRecords[18] = 0
        multipleRecords[19] = 18
        assertNull(WebPushCrypto.decryptAes128Gcm(multipleRecords, rfcKeys()))
    }

    private fun rfcKeys(): WebPushKeyMaterial {
        val parameters = AlgorithmParameters.getInstance("EC")
        parameters.init(ECGenParameterSpec("secp256r1"))
        val curve = parameters.getParameterSpec(ECParameterSpec::class.java)
        val privateKey = KeyFactory.getInstance("EC").generatePrivate(
            ECPrivateKeySpec(BigInteger(1, decode("q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94")), curve),
        )
        return WebPushKeyMaterial(
            publicKey = decode("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4"),
            privateKeyPkcs8 = privateKey.encoded,
            authSecret = decode("BTBZMqHH6r4Tts7J_aSIgg"),
        )
    }

    private fun rfcBody(): ByteArray = decode(
        "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27ml" +
            "mlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPT" +
            "pK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN",
    )

    private fun decode(value: String): ByteArray = Base64.getUrlDecoder().decode(value)
}
