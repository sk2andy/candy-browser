package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.os.SystemClock
import android.util.Log
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoExtensionPersistencePhaseOneInstrumentedTest {
    @Test
    fun installSignedExtensionAndPersistExpectedIdentity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val completed = CountDownLatch(1)
        val result = AtomicReference<GeckoExtensionMutationResult>()
        lateinit var runtime: GeckoViewRuntimeHandle
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            runtime = GeckoRuntimeOwner.getOrCreate(context) as GeckoViewRuntimeHandle
            val managementContext = GeckoExtensionManagementContext(
                profileId = PROFILE_ID,
                isPrivate = false,
            )
            val repository = GeckoExtensionRepository(runtime.extensions) {
                GeckoExtensionPermissionDecision(
                    grantPermissions = true,
                    allowInPrivateBrowsing = false,
                )
            }
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
                repository.refresh()
                repository.snapshot().extensions
                    .firstOrNull(::isSignedFixture)
                    ?.let { existing -> repository.uninstall(existing.id, managementContext) }
                result.set(repository.installSignedXpi(SIGNED_EXTENSION_URI, managementContext))
                completed.countDown()
            }
        }

        assertTrue("Signed XPI install did not finish", completed.await(45, TimeUnit.SECONDS))
        val applied = result.get() as GeckoExtensionMutationResult.Applied
        val extension = checkNotNull(applied.snapshot.extension(applied.extensionId))
        assertTrue(extension.enabled)
        assertFalse(extension.isBuiltIn)
        assertFalse(extension.allowedInPrivateBrowsing)
        assertFalse(extension.temporary)
        assertTrue("Unexpected Gecko disabled flags: ${extension.disabledFlags}", extension.disabledFlags == 0)
        assertTrue("Missing Gecko extension location", extension.location?.isNotBlank() == true)
        assertTrue(isSignedFixture(extension))
        assertTrue(
            "Gecko extension registry was not persisted before process teardown",
            awaitPersistedExtensionRecord(context, extension.id),
        )
        assertTrue(
            "Gecko startup registry was not checkpointed before process teardown",
            awaitGeckoStartupRegistryCheckpoint(
                context = context,
                extensionId = extension.id,
            ),
        )
        SystemClock.sleep(POST_INSTALL_SETTLE_MILLIS)
        assertTrue(
            "Gecko startup registry lost the extension before process teardown",
            isGeckoStartupRegistryCheckpointed(context, extension.id),
        )
        assertTrue(
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_EXTENSION_ID, extension.id)
                .commit(),
        )
        Log.i(
            TEST_TAG,
            "phase1 installed=${extension.id} temporary=${extension.temporary} " +
                "disabledFlags=${extension.disabledFlags} location=${extension.location}",
        )
    }
}

@RunWith(AndroidJUnit4::class)
class GeckoExtensionPersistencePhaseTwoInstrumentedTest {
    @Test
    fun freshRuntimeInventoriesPersistedExtensionWithoutInstalling() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expectedId = checkNotNull(
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(KEY_EXTENSION_ID, null),
        )
        val completed = CountDownLatch(1)
        val listed = AtomicReference<List<GeckoExtension>>()
        lateinit var runtime: GeckoViewRuntimeHandle
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            runtime = GeckoRuntimeOwner.getOrCreate(context) as GeckoViewRuntimeHandle
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
                listed.set(runtime.extensions.listInstalled())
                completed.countDown()
            }
        }

        assertTrue("Fresh Gecko runtime did not return inventory", completed.await(20, TimeUnit.SECONDS))
        val extension = checkNotNull(listed.get().firstOrNull { candidate -> candidate.id == expectedId })
        assertTrue(extension.enabled)
        assertFalse(extension.isBuiltIn)
        assertFalse(extension.allowedInPrivateBrowsing)
        assertFalse(extension.temporary)
        assertTrue("Unexpected Gecko disabled flags: ${extension.disabledFlags}", extension.disabledFlags == 0)
        assertTrue(isSignedFixture(extension))
        Log.i(
            TEST_TAG,
            "phase2 inventoried=${extension.id} temporary=${extension.temporary} " +
                "disabledFlags=${extension.disabledFlags} location=${extension.location}",
        )
    }
}

private fun isSignedFixture(extension: GeckoExtension): Boolean =
    extension.name.orEmpty().contains("Save Screenshot", ignoreCase = true)

private fun awaitPersistedExtensionRecord(context: Context, extensionId: String): Boolean {
    repeat(100) {
        val persisted = context.filesDir.resolve("mozilla")
            .walkTopDown()
            .filter { file -> file.name == "extensions.json" }
            .any { file -> runCatching { file.readText().contains(extensionId) }.getOrDefault(false) }
        if (persisted) return true
        SystemClock.sleep(100)
    }
    return false
}

private fun awaitGeckoStartupRegistryCheckpoint(
    context: Context,
    extensionId: String,
): Boolean {
    repeat(200) {
        val containsExtension = context.filesDir.resolve("mozilla")
            .walkTopDown()
            .firstOrNull { file -> file.name == "addonStartup.json.lz4" }
            ?.let(::readMozillaLz4)
            ?.contains(extensionId) == true
        if (containsExtension) return true
        SystemClock.sleep(100)
    }
    return false
}

private fun isGeckoStartupRegistryCheckpointed(context: Context, extensionId: String): Boolean =
    context.filesDir.resolve("mozilla")
        .walkTopDown()
        .firstOrNull { file -> file.name == "addonStartup.json.lz4" }
        ?.let(::readMozillaLz4)
        ?.contains(extensionId) == true

private fun readMozillaLz4(file: java.io.File): String? = runCatching {
    val encoded = file.readBytes()
    require(encoded.copyOfRange(0, MOZILLA_LZ4_HEADER.size).contentEquals(MOZILLA_LZ4_HEADER))
    val expectedSize = (encoded[8].toInt() and 0xff) or
        ((encoded[9].toInt() and 0xff) shl 8) or
        ((encoded[10].toInt() and 0xff) shl 16) or
        ((encoded[11].toInt() and 0xff) shl 24)
    val decoded = ByteArray(expectedSize)
    var input = 12
    var output = 0
    while (input < encoded.size) {
        val token = encoded[input++].toInt() and 0xff
        var literalLength = token ushr 4
        if (literalLength == 15) {
            var next: Int
            do {
                next = encoded[input++].toInt() and 0xff
                literalLength += next
            } while (next == 255)
        }
        encoded.copyInto(decoded, output, input, input + literalLength)
        input += literalLength
        output += literalLength
        if (input >= encoded.size) break
        val offset = (encoded[input].toInt() and 0xff) or
            ((encoded[input + 1].toInt() and 0xff) shl 8)
        input += 2
        var matchLength = token and 0x0f
        if (matchLength == 15) {
            var next: Int
            do {
                next = encoded[input++].toInt() and 0xff
                matchLength += next
            } while (next == 255)
        }
        matchLength += 4
        repeat(matchLength) {
            decoded[output] = decoded[output - offset]
            output += 1
        }
    }
    decoded.decodeToString(endIndex = output)
}.getOrNull()

private const val SIGNED_EXTENSION_URI =
    "https://addons.mozilla.org/firefox/downloads/latest/savescreenshot/latest.xpi"
private const val PROFILE_ID = "firefox-extension-persistence"
private const val PREFERENCES = "gecko-extension-persistence-test"
private const val KEY_EXTENSION_ID = "extension-id"
private const val TEST_TAG = "CandyExtensionPersistence"
private const val POST_INSTALL_SETTLE_MILLIS = 8_000L
private val MOZILLA_LZ4_HEADER = byteArrayOf(
    'm'.code.toByte(),
    'o'.code.toByte(),
    'z'.code.toByte(),
    'L'.code.toByte(),
    'z'.code.toByte(),
    '4'.code.toByte(),
    '0'.code.toByte(),
    0,
)
