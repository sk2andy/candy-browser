package dev.sk2andy.materialbrowser.sync

import java.security.SecureRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncCryptoTest {
    private val crypto = SyncCrypto(IncrementingSecureRandom())
    private val workspaceKey = ByteArray(32) { (it + 1).toByte() }
    private val fingerprint = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"

    @Test
    fun `matches extension device name and icon vectors`() {
        assertEquals(
            SyncEncryptedValue(
                nonce = "AAECAwQFBgcICQoL",
                ciphertext = "yAvUpB7T3vYfbiKLRC7-Phamzw_Mgt8",
            ),
            crypto.encryptDeviceName(workspaceKey, "workspace-1", fingerprint, "Android"),
        )
        assertEquals(
            SyncEncryptedValue(
                nonce = "AAECAwQFBgcICQoL",
                ciphertext = "YuGzRWcBSl1mlIGLffFZ3pVqxw2LMPqOvrg6VMgMl8tIxUBsDU96zZe0FCjo-OLBDSAvkfo-Y0WH1IG3numQvDi4Qp033Q4",
            ),
            SyncCrypto(IncrementingSecureRandom()).encryptDeviceIcon(
                workspaceKey,
                "workspace-1",
                fingerprint,
                SyncDeviceIconDescriptor("phone", 127),
            ),
        )
    }

    @Test
    fun `matches extension cross-writer tab payload vector`() {
        val metadata = SyncEncryptedChange(
            changeId = "change-1",
            writerDeviceId = "device-writer",
            targetDeviceId = "device-target",
            baseRevision = 3,
            revision = null,
            nonce = "",
            ciphertext = "",
        )
        val snapshot = SyncTabSnapshot(
            capturedAt = "2026-09-02T10:00:00Z",
            tabs = listOf(
                SyncTab("tab-1", 0, 0, null, true, false, "Candy", "https://example.com/"),
            ),
        )
        val encrypted = SyncCrypto(IncrementingSecureRandom()).encryptTabSnapshot(
            workspaceKey,
            metadata,
            snapshot,
        )
        assertEquals("AAECAwQFBgcICQoL", encrypted.nonce)
        assertEquals(snapshot, crypto.decryptTabSnapshot(workspaceKey, encrypted))
        val extensionCiphertext = encrypted.copy(
            ciphertext = "XjbMO4YG3EQgzhySZS6XVKTNNBujCcmp57jiofz3Z36a_x7kz7yyVXbCsZBtPkQNoFl-RFjAQsQ3XCix13MiMVOC8x346oDj_vGfB1kqPFCUog5kcDEHk2T75TNKsZtPEaxsjsadQRosa_r1K8UGM7OtAF05ygVFZp9NC4UC11-LF42tKIy56sQxK1lTjz-qM953Wu9YzSKKu1ej-xOKgrm-87z3Oi3lxj_tY1b39R-iCp_BIm_5Q_deGl9XFsXFxZ9MlKOUyImpBgnIhbv0urrTXvY",
        )
        assertEquals(snapshot, crypto.decryptTabSnapshot(workspaceKey, extensionCiphertext))
    }

    @Test
    fun `P256 identities are unique and fingerprint exact SPKI bytes`() {
        val first = crypto.generateDeviceIdentity()
        val second = crypto.generateDeviceIdentity()
        assertEquals(91, first.publicKeySpki.size)
        assertEquals(32, SyncBase64.decode(first.fingerprint).size)
        assertEquals(crypto.fingerprint(first.publicKeySpki), first.fingerprint)
        assertFalse(first.privateKeyPkcs8.contentEquals(second.privateKeyPkcs8))
        assertNotEquals(first.fingerprint, second.fingerprint)
    }

    @Test
    fun `authenticated fields reject target and writer substitution`() {
        val metadata = SyncEncryptedChange("change", "writer", "target", 0, null, "", "")
        val snapshot = SyncTabSnapshot(
            "2026-09-02T10:00:00Z",
            listOf(SyncTab("tab", 0, 0, null, true, false, "Tab", "https://example.com/")),
        )
        val encrypted = crypto.encryptTabSnapshot(workspaceKey, metadata, snapshot)
        assertThrows(Exception::class.java) {
            crypto.decryptTabSnapshot(workspaceKey, encrypted.copy(targetDeviceId = "other"))
        }
        assertThrows(Exception::class.java) {
            crypto.decryptTabSnapshot(workspaceKey, encrypted.copy(writerDeviceId = "other"))
        }
    }

    @Test
    fun `tab delta round trips and binds tenant mutation and revision metadata`() {
        val mutation = SyncPendingMutation.Navigate(
            mutationId = "mutation-1",
            targetDeviceId = "device-target",
            candyId = "tab-1",
            title = "Candy",
            url = "https://example.com/next",
        )
        val metadata = SyncEncryptedDelta(
            changeId = "change-1",
            mutationId = mutation.mutationId,
            workspaceId = "workspace-1",
            writerDeviceId = "device-writer",
            targetDeviceId = mutation.targetDeviceId,
            baseRevision = 7,
            revision = null,
            nonce = "",
            ciphertext = "",
        )
        val encrypted = SyncCrypto(IncrementingSecureRandom()).encryptTabMutation(
            workspaceKey,
            metadata,
            mutation,
        )

        assertEquals("AAECAwQFBgcICQoL", encrypted.nonce)
        assertEquals(mutation, crypto.decryptTabMutation(workspaceKey, encrypted))
        listOf(
            encrypted.copy(workspaceId = "workspace-2"),
            encrypted.copy(writerDeviceId = "other-writer"),
            encrypted.copy(targetDeviceId = "other-target"),
            encrypted.copy(changeId = "change-2"),
            encrypted.copy(mutationId = "mutation-2"),
            encrypted.copy(baseRevision = 8),
        ).forEach { tampered ->
            assertThrows(Exception::class.java) {
                crypto.decryptTabMutation(workspaceKey, tampered)
            }
        }
    }

    @Test
    fun `matches extension tab delta known answer vector`() {
        val key = ByteArray(32) { it.toByte() }
        val mutation = SyncPendingMutation.Navigate(
            mutationId = "mutation-1",
            targetDeviceId = "desktop-1",
            candyId = "tab-1",
            title = "Example",
            url = "https://example.com/path",
        )
        val metadata = SyncEncryptedDelta(
            changeId = "change-1",
            mutationId = mutation.mutationId,
            workspaceId = "workspace-1",
            writerDeviceId = "phone-1",
            targetDeviceId = mutation.targetDeviceId,
            baseRevision = 7,
            revision = null,
            nonce = "",
            ciphertext = "",
        )

        val encrypted = SyncCrypto(OffsetSecureRandom(0xa0)).encryptTabMutation(
            key,
            metadata,
            mutation,
        )

        assertEquals("oKGio6Slpqeoqaqr", encrypted.nonce)
        assertEquals(
            "5Kht0grOpg8vOfI8gpO4y9SV3GJxOCqB23ihkpH4rukEYkaEEnsrFrgi9dcNep-k4YIwWLXo13ejkU9eMmawkb05Z1DxCUXdB8vRUWHbHYnPZMtIZRMRpbCrSksm9lNsAyB-_RQLINh6mCzZH5-jco6XzHgd6m0xKuFVE_ESwVMZ30fAFDMnqtVPzN2js72qI9wRb3GiT688AOOb8pcosY6jofTVak4qlERI8LAsPSEU",
            encrypted.ciphertext,
        )
        assertEquals(mutation, crypto.decryptTabMutation(key, encrypted))
    }

    @Test
    fun `tab delta accepts the largest protocol reorder payload`() {
        val mutation = SyncPendingMutation.Reorder(
            mutationId = "mutation-largest",
            targetDeviceId = "desktop-1",
            orderedCandyIds = List(1_000) { index ->
                "tab-${index.toString().padStart(4, '0')}-${"x".repeat(119)}"
            },
        )
        val metadata = SyncEncryptedDelta(
            changeId = "change-largest",
            mutationId = mutation.mutationId,
            workspaceId = "workspace-1",
            writerDeviceId = "phone-1",
            targetDeviceId = mutation.targetDeviceId,
            baseRevision = 7,
            revision = null,
            nonce = "",
            ciphertext = "",
        )

        val encrypted = crypto.encryptTabMutation(workspaceKey, metadata, mutation)

        assertEquals(mutation, crypto.decryptTabMutation(workspaceKey, encrypted))
    }

    private class IncrementingSecureRandom : SecureRandom() {
        private var next = 0

        override fun nextBytes(bytes: ByteArray) {
            bytes.indices.forEach { index -> bytes[index] = (next++).toByte() }
        }
    }

    private class OffsetSecureRandom(
        private var next: Int,
    ) : SecureRandom() {
        override fun nextBytes(bytes: ByteArray) {
            bytes.indices.forEach { index -> bytes[index] = (next++).toByte() }
        }
    }
}
