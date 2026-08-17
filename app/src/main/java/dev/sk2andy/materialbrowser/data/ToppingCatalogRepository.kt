package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.sk2andy.materialbrowser.browser.userscript.ToppingCatalog
import dev.sk2andy.materialbrowser.browser.userscript.ToppingCatalogEntry
import dev.sk2andy.materialbrowser.browser.userscript.ToppingCatalogParseResult
import dev.sk2andy.materialbrowser.browser.userscript.ToppingCatalogParser
import dev.sk2andy.materialbrowser.browser.userscript.ToppingCatalogRejectionReason
import dev.sk2andy.materialbrowser.browser.userscript.ToppingVerificationResult
import dev.sk2andy.materialbrowser.browser.userscript.ToppingVerifier
import dev.sk2andy.materialbrowser.browser.userscript.UserScript
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptRejectionReason
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal sealed interface ToppingCatalogRefreshFailure {
    data class Remote(val reason: ToppingRemoteFailure) : ToppingCatalogRefreshFailure

    data object InvalidUtf8 : ToppingCatalogRefreshFailure

    data class InvalidManifest(
        val reason: ToppingCatalogRejectionReason,
    ) : ToppingCatalogRefreshFailure
}

internal sealed interface ToppingCatalogRefreshResult {
    data class Fresh(val catalog: ToppingCatalog) : ToppingCatalogRefreshResult

    data class Cached(
        val catalog: ToppingCatalog,
        val refreshFailure: ToppingCatalogRefreshFailure,
    ) : ToppingCatalogRefreshResult

    data class Error(val reason: ToppingCatalogRefreshFailure) : ToppingCatalogRefreshResult
}

internal sealed interface ToppingDownloadResult {
    data class Accepted(val script: UserScript) : ToppingDownloadResult

    data class RemoteError(val reason: ToppingRemoteFailure) : ToppingDownloadResult

    data object IntegrityMismatch : ToppingDownloadResult

    data object InvalidUtf8 : ToppingDownloadResult

    data class InvalidScript(val reason: UserScriptRejectionReason) : ToppingDownloadResult

    data object MetadataMismatch : ToppingDownloadResult
}

internal class ToppingCatalogRepository internal constructor(
    private val store: ToppingCatalogStore,
    private val remote: ToppingCatalogRemoteSource,
    private val executor: ExecutorService,
    private val mainHandler: Handler,
    private val clock: () -> Long,
) {
    internal constructor(context: Context) : this(
        store = ToppingCatalogStore(context.applicationContext),
        remote = GitHubToppingCatalogClient(),
        executor = newExecutor(),
        mainHandler = Handler(Looper.getMainLooper()),
        clock = System::currentTimeMillis,
    )

    fun refresh(onComplete: (ToppingCatalogRefreshResult) -> Unit) {
        executor.execute {
            val result = refreshBlocking()
            mainHandler.post { onComplete(result) }
        }
    }

    fun download(
        entry: ToppingCatalogEntry,
        onComplete: (ToppingDownloadResult) -> Unit,
    ) {
        executor.execute {
            val result = downloadBlocking(entry)
            mainHandler.post { onComplete(result) }
        }
    }

    fun clearCache() {
        executor.execute(store::clear)
    }

    private fun refreshBlocking(): ToppingCatalogRefreshResult {
        val fresh = when (val fetched = remote.fetchManifest()) {
            is ToppingRemoteResult.Failure -> {
                return fallback(ToppingCatalogRefreshFailure.Remote(fetched.reason))
            }
            is ToppingRemoteResult.Success -> fetched.bytes
        }
        val json = ToppingVerifier.decodeUtf8(fresh)
            ?: return fallback(ToppingCatalogRefreshFailure.InvalidUtf8)
        val parsed = when (val result = ToppingCatalogParser.parse(json)) {
            is ToppingCatalogParseResult.Accepted -> result.catalog
            is ToppingCatalogParseResult.Rejected -> {
                return fallback(ToppingCatalogRefreshFailure.InvalidManifest(result.reason))
            }
        }
        if (!store.save(fresh)) Log.w(TAG, "Fresh Topping catalog could not be cached")
        return ToppingCatalogRefreshResult.Fresh(parsed)
    }

    private fun fallback(failure: ToppingCatalogRefreshFailure): ToppingCatalogRefreshResult =
        store.load()?.let { cached ->
            ToppingCatalogRefreshResult.Cached(
                catalog = cached.catalog,
                refreshFailure = failure,
            )
        } ?: ToppingCatalogRefreshResult.Error(failure)

    private fun downloadBlocking(entry: ToppingCatalogEntry): ToppingDownloadResult {
        val bytes = when (val fetched = remote.fetchTopping(entry)) {
            is ToppingRemoteResult.Failure -> return ToppingDownloadResult.RemoteError(fetched.reason)
            is ToppingRemoteResult.Success -> fetched.bytes
        }
        return when (
            val verified = ToppingVerifier.verify(
                entry = entry,
                bytes = bytes,
                updatedAtMillis = clock().coerceAtLeast(0L),
            )
        ) {
            is ToppingVerificationResult.Accepted -> ToppingDownloadResult.Accepted(verified.script)
            ToppingVerificationResult.IntegrityMismatch -> ToppingDownloadResult.IntegrityMismatch
            ToppingVerificationResult.InvalidUtf8 -> ToppingDownloadResult.InvalidUtf8
            is ToppingVerificationResult.InvalidScript -> {
                ToppingDownloadResult.InvalidScript(verified.reason)
            }
            ToppingVerificationResult.MetadataMismatch -> ToppingDownloadResult.MetadataMismatch
        }
    }

    companion object {
        private const val TAG = "ToppingCatalogRepo"

        @Volatile
        private var instance: ToppingCatalogRepository? = null

        fun get(context: Context): ToppingCatalogRepository = instance ?: synchronized(this) {
            instance ?: ToppingCatalogRepository(context.applicationContext).also { instance = it }
        }

        private fun newExecutor(): ExecutorService =
            Executors.newSingleThreadExecutor { task -> Thread(task, "topping-catalog-io") }
    }
}
