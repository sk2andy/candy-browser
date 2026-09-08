package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

internal object GeckoDefaultExtensionCatalog {
    private const val CATALOG_ASSET = "gecko_default_extensions/catalog.json"
    val retiredExtensionIds = setOf("jid1-KKzOGWgsW3Ao4Q@jetpack")

    suspend fun load(context: Context): List<GeckoDefaultExtension> =
        withContext(Dispatchers.IO) {
            context.assets.open(CATALOG_ASSET).bufferedReader(Charsets.UTF_8).use { reader ->
                parse(reader.readText())
            }
        }

    fun parse(rawCatalog: String): List<GeckoDefaultExtension> {
        val root = JSONObject(rawCatalog)
        require(root.getInt("schemaVersion") == 1) {
            "Unsupported default Gecko extension catalog schema"
        }
        val rawExtensions = root.getJSONArray("extensions")
        val parsed = buildList(rawExtensions.length()) {
            repeat(rawExtensions.length()) { index ->
                val raw = rawExtensions.getJSONObject(index)
                val delivery = when (raw.getString("delivery")) {
                    "bundled" -> GeckoDefaultExtensionDelivery.Bundled
                    "remote" -> GeckoDefaultExtensionDelivery.Remote
                    else -> error("Unsupported default Gecko extension delivery")
                }
                add(
                    GeckoDefaultExtension(
                        id = raw.getString("id"),
                        name = raw.getString("name"),
                        version = raw.getString("version"),
                        installUri = raw.getString("installUri"),
                        sha256 = raw.getString("sha256"),
                        size = raw.getLong("size"),
                        delivery = delivery,
                        assetPath = raw.optString("assetPath").takeIf(String::isNotBlank),
                    ),
                )
            }
        }
        GeckoDefaultExtensionRules.requireValidCatalog(parsed)
        return parsed
    }
}

internal class GeckoDefaultExtensionPreferences(context: Context) :
    GeckoDefaultExtensionStateStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override suspend fun read(extensionId: String): GeckoDefaultExtensionState? =
        withContext(Dispatchers.IO) {
            when (preferences.getString(key(extensionId), null)) {
                STATE_PROVISIONED -> GeckoDefaultExtensionState.Provisioned
                STATE_REMOVED -> GeckoDefaultExtensionState.Removed
                else -> null
            }
        }

    override suspend fun write(
        extensionId: String,
        state: GeckoDefaultExtensionState,
    ): Boolean = withContext(Dispatchers.IO) {
        preferences.edit()
            .putString(
                key(extensionId),
                when (state) {
                    GeckoDefaultExtensionState.Provisioned -> STATE_PROVISIONED
                    GeckoDefaultExtensionState.Removed -> STATE_REMOVED
                },
            )
            .commit()
    }

    private fun key(extensionId: String): String = "extension.$extensionId"

    private companion object {
        const val PREFERENCES_NAME = "gecko-default-extensions-v1"
        const val STATE_PROVISIONED = "provisioned"
        const val STATE_REMOVED = "removed"
    }
}

internal class GeckoDefaultExtensionAssets(context: Context) :
    GeckoDefaultExtensionAssetVerifier {
    private val assets = context.applicationContext.assets

    override suspend fun verify(extension: GeckoDefaultExtension): Boolean =
        withContext(Dispatchers.IO) {
            if (extension.delivery == GeckoDefaultExtensionDelivery.Remote) {
                return@withContext true
            }
            val assetPath = extension.assetPath ?: return@withContext false
            runCatching {
                val digest = MessageDigest.getInstance("SHA-256")
                assets.open(assetPath).use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                    }
                }
                digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) } ==
                    extension.sha256
            }.getOrDefault(false)
        }
}

internal class GeckoViewDefaultExtensionInstaller(
    context: Context,
    private val controller: WebExtensionController,
) : GeckoDefaultExtensionInstaller {
    private val installMutex = Mutex()
    private val remoteSource = GeckoDefaultExtensionRemoteSource(context.applicationContext)

    @Volatile
    private var pendingInstall: GeckoDefaultExtension? = null

    override suspend fun listInstalled(): List<GeckoExtension> =
        controller.list().awaitDefaultExtensionResult().map(WebExtension::toDefaultExtension)

    override suspend fun install(extension: GeckoDefaultExtension): GeckoExtension =
        installMutex.withLock {
            check(pendingInstall == null) { "Default Gecko extension install already active" }
            pendingInstall = extension
            var downloaded: File? = null
            try {
                val installUri = if (extension.delivery == GeckoDefaultExtensionDelivery.Remote) {
                    remoteSource.download(extension).also { file -> downloaded = file }
                        .toURI()
                        .toASCIIString()
                } else {
                    extension.installUri
                }
                controller.install(
                    installUri,
                    WebExtensionController.INSTALLATION_METHOD_ONBOARDING,
                ).awaitDefaultExtensionResult().toDefaultExtension()
            } finally {
                pendingInstall = null
                withContext(NonCancellable + Dispatchers.IO) {
                    downloaded?.delete()
                }
            }
        }

    override suspend fun uninstall(extensionId: String) {
        val installed = controller.list().awaitDefaultExtensionResult()
            .firstOrNull { extension -> extension.id == extensionId }
            ?: return
        controller.uninstall(installed).awaitDefaultExtensionCompletion()
    }

    fun automaticPermissionDecision(extension: WebExtension): GeckoExtensionPermissionDecision? {
        val expected = pendingInstall ?: return null
        if (extension.id != expected.id || extension.metaData.version != expected.version) return null
        return GeckoExtensionPermissionDecision(
            grantPermissions = true,
            allowInPrivateBrowsing = false,
        )
    }
}

private class GeckoDefaultExtensionRemoteSource(context: Context) {
    private val cacheDirectory = File(context.cacheDir, CACHE_DIRECTORY)

    suspend fun download(extension: GeckoDefaultExtension): File = withContext(Dispatchers.IO) {
        require(extension.delivery == GeckoDefaultExtensionDelivery.Remote)
        cacheDirectory.mkdirs()
        check(cacheDirectory.isDirectory) { "Default extension cache directory is unavailable" }
        val temporary = File.createTempFile("default-extension-", ".xpi", cacheDirectory)
        try {
            val connection = URL(extension.installUri).openConnection() as HttpURLConnection
            connection.connectTimeout = NETWORK_TIMEOUT_MILLIS
            connection.readTimeout = NETWORK_TIMEOUT_MILLIS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            try {
                check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                    "Default extension download failed with HTTP ${connection.responseCode}"
                }
                GeckoDefaultExtensionDownloadRules.requirePinnedAmoUrl(connection.url.toString())
                connection.inputStream.use { input ->
                    temporary.outputStream().use { output ->
                        GeckoDefaultExtensionDownloadRules.copyAndVerify(
                            input = input,
                            output = output,
                            expectedSize = extension.size,
                            expectedSha256 = extension.sha256,
                        )
                    }
                }
                temporary
            } finally {
                connection.disconnect()
            }
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    private companion object {
        const val CACHE_DIRECTORY = "gecko-default-extensions"
        const val NETWORK_TIMEOUT_MILLIS = 30_000
        const val USER_AGENT = "Candy-Browser-default-extension/1"
    }
}

internal object GeckoDefaultExtensionDownloadRules {
    fun requirePinnedAmoUrl(rawUrl: String) {
        val uri = URI(rawUrl)
        check(
            uri.scheme.equals("https", ignoreCase = true) &&
                uri.host.equals("addons.mozilla.org", ignoreCase = true) &&
                uri.port in setOf(-1, 443) &&
                uri.rawUserInfo == null &&
                uri.rawQuery == null &&
                uri.rawFragment == null &&
                AMO_FILE_PATH.matches(uri.rawPath.orEmpty()),
        ) { "Default extension download redirected outside pinned AMO source" }
    }

    fun copyAndVerify(
        input: InputStream,
        output: OutputStream,
        expectedSize: Long,
        expectedSha256: String,
    ) {
        require(expectedSize > 0)
        require(expectedSha256.matches(SHA_256_PATTERN))
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            check(total <= expectedSize) { "Default extension download exceeds pinned size" }
            digest.update(buffer, 0, read)
            output.write(buffer, 0, read)
        }
        check(total == expectedSize) { "Default extension download size mismatch" }
        val actualHash = digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
        check(actualHash == expectedSha256) { "Default extension download SHA-256 mismatch" }
    }

    private val AMO_FILE_PATH = Regex(
        "/firefox/downloads/file/[0-9]+/[A-Za-z0-9][A-Za-z0-9._-]*\\.xpi",
    )
    private val SHA_256_PATTERN = Regex("[0-9a-f]{64}")
}

internal class GeckoDefaultExtensionStartup(
    scope: CoroutineScope,
    runAfterInternalHostInitialization: ((Boolean) -> Unit) -> Unit,
    private val provisionerFactory: suspend () -> GeckoDefaultExtensionProvisioner,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val completion = CompletableDeferred<Unit>()
    private val retryMutex = Mutex()
    private var retryAttempts = 0
    private var lastRetryNanos: Long? = null
    private var provisioner: GeckoDefaultExtensionProvisioner? = null

    @Volatile
    var result: GeckoDefaultExtensionProvisioningResult? = null
        private set

    init {
        runAfterInternalHostInitialization { initialized ->
            if (!initialized) {
                completion.completeExceptionally(
                    IllegalStateException("Candy internal extension hosts failed to initialize"),
                )
                return@runAfterInternalHostInitialization
            }
            scope.launch {
                try {
                    val created = provisionerFactory()
                    provisioner = created
                    result = created.provision()
                    if (result?.hasFailure == true) lastRetryNanos = nanoTime()
                    completion.complete(Unit)
                } catch (error: CancellationException) {
                    completion.cancel(error)
                    throw error
                } catch (error: Throwable) {
                    completion.completeExceptionally(error)
                }
            }
        }
    }

    suspend fun await() = completion.await()

    suspend fun retryFailedIfDue() {
        await()
        retryMutex.withLock {
            if (retryAttempts >= MAX_RETRY_ATTEMPTS || result?.hasFailure != true) return
            val now = nanoTime()
            val lastRetry = lastRetryNanos
            if (lastRetry != null && now - lastRetry < RETRY_INTERVAL_NANOS) return
            retryAttempts++
            try {
                result = requireNotNull(provisioner).provision()
            } finally {
                lastRetryNanos = nanoTime()
            }
        }
    }

    suspend fun markRemovalRequested(extensionId: String): Boolean {
        await()
        return retryMutex.withLock {
            requireNotNull(provisioner).markRemovalRequested(extensionId)
        }
    }

    suspend fun markUserInstallSucceeded(extensionId: String): Boolean {
        await()
        return retryMutex.withLock {
            requireNotNull(provisioner).markUserInstallSucceeded(extensionId)
        }
    }

    private companion object {
        const val MAX_RETRY_ATTEMPTS = 3
        const val RETRY_INTERVAL_NANOS = 30_000_000_000L
    }
}

private val GeckoDefaultExtensionProvisioningResult.hasFailure: Boolean
    get() = outcomes.values.any { outcome ->
        outcome == GeckoDefaultExtensionProvisioningOutcome.Failed
    }

private fun WebExtension.toDefaultExtension() = GeckoExtension(
    id = id,
    name = metaData.name,
    version = metaData.version,
    enabled = metaData.enabled,
    allowedInPrivateBrowsing = metaData.allowedInPrivateBrowsing,
    isBuiltIn = isBuiltIn,
    temporary = metaData.temporary,
    signedState = metaData.signedState,
    disabledFlags = metaData.disabledFlags,
    location = location,
    baseUrl = metaData.baseUrl,
    optionsPageUrl = metaData.optionsPageUrl,
    opensOptionsPageInTab = metaData.openOptionsPageInTab,
)

private suspend fun <T> GeckoResult<T>.awaitDefaultExtensionResult(): T =
    suspendCancellableCoroutine { continuation ->
        accept(
            { value ->
                if (!continuation.isActive) return@accept
                if (value == null) {
                    continuation.resumeWithException(
                        IllegalStateException("GeckoResult completed without a value"),
                    )
                } else {
                    continuation.resume(value)
                }
            },
            { error ->
                if (!continuation.isActive) return@accept
                continuation.resumeWithException(
                    error ?: IllegalStateException("GeckoResult failed without an exception"),
                )
            },
        )
        continuation.invokeOnCancellation { cancel() }
    }

private suspend fun GeckoResult<*>.awaitDefaultExtensionCompletion(): Unit =
    suspendCancellableCoroutine { continuation ->
        accept(
            { _ -> if (continuation.isActive) continuation.resume(Unit) },
            { error ->
                if (!continuation.isActive) return@accept
                continuation.resumeWithException(
                    error ?: IllegalStateException("GeckoResult failed without an exception"),
                )
            },
        )
        continuation.invokeOnCancellation { cancel() }
    }
