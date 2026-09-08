package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoDefaultExtensionProvisioningInstrumentedTest {
    @Test
    fun bundledDefaultInstallsThroughSignedPersistentResourcePipeline() {
        val installed = installedDefaults()
        val uBlock = checkNotNull(installed.firstOrNull { extension -> extension.id == U_BLOCK_ID })

        assertDefaultExtension(uBlock, U_BLOCK_VERSION)
    }

    @Test
    fun onlineFallbackInstallsPinnedCookieExtensionWithoutPrivateAccess() {
        val installed = installedDefaults()
        val cookieExtension = checkNotNull(
            installed.firstOrNull { extension -> extension.id == COOKIE_EXTENSION_ID },
        )

        assertDefaultExtension(cookieExtension, COOKIE_EXTENSION_VERSION)
    }

    private fun installedDefaults(): List<GeckoExtension> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val completed = CountDownLatch(1)
        val installed = AtomicReference<List<GeckoExtension>>()
        val failure = AtomicReference<Throwable>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val runtime = GeckoRuntimeOwner.getOrCreate(context)
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
                try {
                    installed.set(runtime.extensions.listInstalled())
                } catch (error: Throwable) {
                    failure.set(error)
                } finally {
                    completed.countDown()
                }
            }
        }
        assertTrue(
            "Default Gecko extension provisioning timed out",
            completed.await(90, TimeUnit.SECONDS),
        )
        failure.get()?.let { error -> throw AssertionError("Default provisioning failed", error) }
        return installed.get().orEmpty()
    }

    private fun assertDefaultExtension(extension: GeckoExtension, version: String) {
        assertEquals(version, extension.version)
        assertTrue(extension.enabled)
        assertFalse(extension.allowedInPrivateBrowsing)
        assertFalse(extension.isBuiltIn)
        assertFalse(extension.temporary)
        assertTrue("Extension is not Mozilla-signed: ${extension.signedState}", extension.signedState >= 2)
    }

    private companion object {
        const val U_BLOCK_ID = "uBlock0@raymondhill.net"
        const val U_BLOCK_VERSION = "1.74.0"
        const val COOKIE_EXTENSION_ID = "idcac-pub@guus.ninja"
        const val COOKIE_EXTENSION_VERSION = "1.1.9"
    }
}
