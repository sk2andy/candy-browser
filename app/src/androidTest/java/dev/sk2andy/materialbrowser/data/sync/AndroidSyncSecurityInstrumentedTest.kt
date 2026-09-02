package dev.sk2andy.materialbrowser.data.sync

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.sync.AndroidArgon2RecoveryKeyDeriver
import dev.sk2andy.materialbrowser.sync.SyncBase64
import dev.sk2andy.materialbrowser.sync.SyncCache
import dev.sk2andy.materialbrowser.sync.SyncDeviceIconCatalog
import dev.sk2andy.materialbrowser.sync.SyncRecoveryKdf
import dev.sk2andy.materialbrowser.sync.SyncVaultSecrets
import java.io.File
import java.security.KeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidSyncSecurityInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun clearState() {
        AndroidSyncVaultStore(context).clear()
        AndroidSyncCacheStore(context).clear()
        deleteKey(TEST_OTHER_ALIAS)
    }

    @After
    fun cleanUp() {
        AndroidSyncVaultStore(context).clear()
        AndroidSyncCacheStore(context).clear()
        deleteKey(TEST_OTHER_ALIAS)
    }

    @Test
    fun argon2idMatchesExtensionRecoveryVector() {
        val passphrase = "interop passphrase".toByteArray()
        val result = AndroidArgon2RecoveryKeyDeriver().derive(
            passphrase,
            SyncRecoveryKdf(
                salt = SyncBase64.encode(ByteArray(16) { it.toByte() }),
                memoryKiB = 65_536,
                iterations = 3,
                parallelism = 4,
            ),
        )
        assertEquals(
            "d2797f27149b30db25bdac57fc9000814895efd14aa340505319c143b1e4c983",
            result.joinToString("") { "%02x".format(it) },
        )
        passphrase.fill(0)
        result.fill(0)
    }

    @Test
    fun keystoreVaultRoundTripsWithoutPlaintextAtRest() {
        val secrets = SyncVaultSecrets(
            workspaceId = "workspace-1",
            deviceId = "device-1",
            deviceToken = "secret-bearer-token",
            workspaceKey = ByteArray(32) { 7 },
            devicePrivateKeyPkcs8 = ByteArray(138) { 9 },
        )
        val store = AndroidSyncVaultStore(context)
        assertTrue(store.save(secrets))
        assertEquals(secrets, store.load())
        val raw = File(context.noBackupFilesDir, "candy_sync_vault_v1").readText()
        assertFalse(raw.contains("secret-bearer-token"))
        assertFalse(raw.contains(SyncBase64.encode(secrets.workspaceKey)))
    }

    @Test
    fun keystoreCiphertextRejectsWrongKeyAndTampering() {
        val protector = AndroidSyncSecretProtector()
        val encrypted = protector.encrypt("secret".toByteArray(), "aad".toByteArray())
        val wrong = AndroidSyncSecretProtector(TEST_OTHER_ALIAS)
        assertTrue(runCatching { wrong.decrypt(encrypted, "aad".toByteArray()) }.isFailure)

        val store = AndroidSyncVaultStore(context)
        assertTrue(
            store.save(
                SyncVaultSecrets("workspace", "device", "token", ByteArray(32), ByteArray(64)),
            ),
        )
        val file = File(context.noBackupFilesDir, "candy_sync_vault_v1")
        val bytes = file.readBytes()
        bytes[bytes.lastIndex / 2] = (bytes[bytes.lastIndex / 2].toInt() xor 1).toByte()
        file.writeBytes(bytes)
        assertNull(store.load())
    }

    @Test
    fun encryptedCacheAndGeneratedIconCatalogAreAvailable() {
        val cacheStore = AndroidSyncCacheStore(context)
        val cache = SyncCache(cursor = "epoch:1", profiles = emptyMap())
        assertTrue(cacheStore.save(cache))
        assertEquals(cache, cacheStore.load())
        assertFalse(File(context.noBackupFilesDir, "candy_sync_cache_v1").readText().contains("epoch:1"))

        val catalog = context.assets.open("candy_sync_device_icons_v1.json").use(SyncDeviceIconCatalog::decode)
        assertEquals(54, catalog.icons.size)
        assertTrue(catalog.contains("phone"))
        assertTrue(catalog.contains("computer"))
    }

    private fun deleteKey(alias: String) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(alias)) store.deleteEntry(alias)
    }

    private companion object {
        const val TEST_OTHER_ALIAS = "candy_sync_test_other_key"
    }
}
