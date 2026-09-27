package dev.sk2andy.materialbrowser.browser.gecko.webpush

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FossWebPushStoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = FossWebPushStore(context)

    @Before
    fun setUp() {
        store.clear()
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun subscriptionAndKeysRoundTripEncrypted() {
        val keys = WebPushCrypto.generateKeyMaterial()
        val subscription = FossPushSubscription(
            scope = "https://example.com/sw/^userContextId=2",
            endpoint = "https://updates.push.services.mozilla.com/wpush/example",
            channelId = "11111111-2222-3333-4444-555555555555",
            appServerKey = ByteArray(65) { 4 },
            keys = keys,
        )
        assertTrue(
            store.save(
                FossWebPushState(
                    uaid = "0123456789abcdef0123456789abcdef",
                    subscriptions = listOf(subscription),
                    deliveredVersions = mapOf(subscription.channelId to listOf("opaque:v1")),
                ),
            ),
        )

        val restored = FossWebPushStore(context).load()
        assertEquals("0123456789abcdef0123456789abcdef", restored.uaid)
        assertEquals(subscription.scope, restored.subscriptions.single().scope)
        assertArrayEquals(keys.privateKeyPkcs8, restored.subscriptions.single().keys.privateKeyPkcs8)
        assertArrayEquals(keys.authSecret, restored.subscriptions.single().keys.authSecret)
        assertEquals(listOf("opaque:v1"), restored.deliveredVersions[subscription.channelId])
        val ciphertext = File(context.noBackupFilesDir, "candy_web_push_foss_v1")
        assertTrue(ciphertext.exists())
        assertEquals(context.noBackupFilesDir, ciphertext.parentFile)
        val encoded = ciphertext.readText(Charsets.UTF_8)
        assertFalse(encoded.contains("https://example.com"))
    }

    @Test
    fun privateScopeIsRejectedAtPersistenceBoundary() {
        val subscription = FossPushSubscription(
            scope = "https://example.com/sw/^privateBrowsingId=1",
            endpoint = "https://updates.push.services.mozilla.com/wpush/example",
            channelId = "11111111-2222-3333-4444-555555555555",
            appServerKey = null,
            keys = WebPushCrypto.generateKeyMaterial(),
        )
        assertFalse(store.save(FossWebPushState(subscriptions = listOf(subscription))))
        assertTrue(store.load().subscriptions.isEmpty())
    }

    @Test
    fun corruptEncryptedStateIsIgnored() {
        val file = File(context.noBackupFilesDir, "candy_web_push_foss_v1")
        file.writeText("corrupt")
        assertEquals(FossWebPushState(), FossWebPushStore(context).load())
    }

    @Test
    fun clearingDeletesLocalPushIdentityAndKeys() {
        val keys = WebPushCrypto.generateKeyMaterial()
        val subscription = FossPushSubscription(
            scope = "https://example.com/sw/",
            endpoint = "https://updates.push.services.mozilla.com/wpush/example",
            channelId = "11111111-2222-3333-4444-555555555555",
            appServerKey = null,
            keys = keys,
        )
        assertTrue(store.save(FossWebPushState(
            uaid = "0123456789abcdef0123456789abcdef",
            subscriptions = listOf(subscription),
        )))

        assertTrue(store.clear())
        assertFalse(File(context.noBackupFilesDir, "candy_web_push_foss_v1").exists())
        assertEquals(FossWebPushState(), FossWebPushStore(context).load())
    }
}
