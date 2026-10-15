package dev.sk2andy.materialbrowser.browser.integration.devicesearch

import android.app.appsearch.AppSearchBatchResult
import android.app.appsearch.AppSearchManager
import android.app.appsearch.AppSearchSchema
import android.app.appsearch.AppSearchSession
import android.app.appsearch.BatchResultCallback
import android.app.appsearch.GenericDocument
import android.app.appsearch.PutDocumentsRequest
import android.app.appsearch.SearchSpec
import android.app.appsearch.SetSchemaRequest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Explicitly selected, device-local documents. Never indexes browsing history or private content. */
object DeviceSearchIndexer {
    internal const val DATABASE_NAME = "candy_device_search"
    internal const val NAMESPACE = "shared_items"
    internal const val PAGE_SCHEMA = "builtin:WebPage"
    internal const val ACTION_SCHEMA = "builtin:PotentialAction"
    private const val DOCUMENT_TTL_MILLIS = 6 * 60 * 60 * 1_000L
    private val writer = Executors.newSingleThreadExecutor { task -> Thread(task, "device-search-index") }
    private val callbackExecutor = Executor { task -> task.run() }
    private val generation = AtomicLong()
    private val handler = Handler(Looper.getMainLooper())
    private var installed = false
    private var sourceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var settingsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var readerObserver: FileObserver? = null

    @Synchronized
    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        sourceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key in setOf(
                    BrowserSessionStore.KEY_PROFILES,
                    BrowserSessionStore.KEY_PROFILES_ENABLED,
                    BrowserSessionStore.KEY_FAVORITES,
                )
            ) refresh(appContext)
        }.also {
            appContext.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(it)
        }
        settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh(appContext) }.also {
            appContext.getSharedPreferences(DeviceSearchStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(it)
        }
        readerObserver = object : FileObserver(
            appContext.filesDir,
            FileObserver.MOVED_TO or FileObserver.CLOSE_WRITE or FileObserver.DELETE,
        ) {
            override fun onEvent(event: Int, path: String?) {
                if (path == "reader_library_v1.json") refresh(appContext)
            }
        }.also(FileObserver::startWatching)
        refresh(appContext)
    }

    internal fun sourceRevision(): Long = generation.get()

    fun refresh(context: Context, onFinished: ((Boolean) -> Unit)? = null) {
        val appContext = context.applicationContext
        val expectedGeneration = generation.incrementAndGet()
        writer.execute {
            val success = runCatching {
                rebuild(appContext, expectedGeneration)
            }.getOrDefault(false)
            if (onFinished != null) handler.post { onFinished(success) }
        }
    }

    private fun rebuild(context: Context, expectedGeneration: Long): Boolean {
        if (generation.get() != expectedGeneration) return false
        val sources = loadDeviceSearchSources(context)
        DeviceSearchRefreshScheduler.update(context, sources.settings.enabled)
        val validIds = sources.items.mapTo(hashSetOf(), DeviceSearchItem::id)
        val settings = sources.settings.copy(selectedIds = sources.settings.selectedIds.intersect(validIds))
        if (settings != sources.settings && !DeviceSearchStore(context).save(settings)) return false
        val manager = context.getSystemService(AppSearchManager::class.java) ?: return false
        val sessionFuture = CompletableFuture<AppSearchSession>()
        manager.createSearchSession(
            AppSearchManager.SearchContext.Builder(DATABASE_NAME).build(),
            callbackExecutor,
        ) { result ->
            if (result.isSuccess) sessionFuture.complete(result.resultValue)
            else sessionFuture.completeExceptionally(IllegalStateException(result.errorMessage))
        }
        sessionFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).use { session ->
            // Revoke visibility before changing documents, including failed or interrupted rebuilds.
            setSchema(session, displayed = false)
            val removed = CompletableFuture<Unit>()
            session.remove("", SearchSpec.Builder().addFilterNamespaces(NAMESPACE).build(), callbackExecutor) { result ->
                if (result.isSuccess) removed.complete(Unit)
                else removed.completeExceptionally(IllegalStateException(result.errorMessage))
            }
            removed.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            val items = DeviceSearchRules.published(settings, sources.items)
            if (items.isNotEmpty()) {
                val result = CompletableFuture<Boolean>()
                session.put(
                    PutDocumentsRequest.Builder().addGenericDocuments(items.map { document(context, it) }).build(),
                    callbackExecutor,
                    object : BatchResultCallback<String, Void> {
                        override fun onResult(batch: AppSearchBatchResult<String, Void>) {
                            result.complete(batch.isSuccess)
                        }

                        override fun onSystemError(error: Throwable?) {
                            result.completeExceptionally(error ?: IllegalStateException("AppSearch write failed"))
                        }
                    },
                )
                if (!result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) return false
            }
            if (generation.get() != expectedGeneration) return false
            setSchema(session, displayed = settings.enabled && items.isNotEmpty())
            return true
        }
    }

    private fun setSchema(session: AppSearchSession, displayed: Boolean) {
        val action = AppSearchSchema.Builder(ACTION_SCHEMA)
            .addProperty(stringProperty("name", indexed = false))
            .addProperty(stringProperty("description", indexed = false))
            .addProperty(stringProperty("uri", indexed = false))
            .build()
        val page = AppSearchSchema.Builder(PAGE_SCHEMA)
            .addProperty(stringProperty("name", indexed = true))
            .addProperty(stringProperty("url", indexed = false))
            .addProperty(stringProperty("description", indexed = true))
            .addProperty(
                AppSearchSchema.DocumentPropertyConfig.Builder("potentialActions", ACTION_SCHEMA)
                    .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_REPEATED)
                    .build(),
            )
            .build()
        val result = CompletableFuture<Unit>()
        session.setSchema(
            SetSchemaRequest.Builder().addSchemas(action, page)
                .setSchemaTypeDisplayedBySystem(PAGE_SCHEMA, displayed)
                .setSchemaTypeDisplayedBySystem(ACTION_SCHEMA, displayed)
                .setForceOverride(true)
                .build(),
            callbackExecutor,
            callbackExecutor,
        ) { outcome ->
            if (outcome.isSuccess) result.complete(Unit)
            else result.completeExceptionally(IllegalStateException(outcome.errorMessage))
        }
        result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    internal fun document(context: Context, item: DeviceSearchItem): GenericDocument {
        val intent = DeviceSearchContract.openIntent(context, item.id)
        val action = DocumentBuilder(NAMESPACE, "open", ACTION_SCHEMA)
            .setPropertyString("name", "actions.intent.OPEN_APP_FEATURE")
            .setPropertyString("description", context.getString(dev.sk2andy.materialbrowser.R.string.device_search_open))
            .setPropertyString("uri", intent.toUri(Intent.URI_INTENT_SCHEME))
            .build()
        return DocumentBuilder(NAMESPACE, item.id, PAGE_SCHEMA)
            .setPropertyString("name", item.title)
            .setPropertyString("url", requireNotNull(intent.dataString))
            .setPropertyString("description", item.sourceUrl)
            .setPropertyDocument("potentialActions", action)
            .setTtlMillis(DOCUMENT_TTL_MILLIS)
            .build()
    }

    private fun stringProperty(name: String, indexed: Boolean): AppSearchSchema.StringPropertyConfig =
        AppSearchSchema.StringPropertyConfig.Builder(name)
            .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
            .setIndexingType(
                if (indexed) AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES
                else AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_NONE,
            )
            .setTokenizerType(
                if (indexed) AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN
                else AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_NONE,
            )
            .build()

    private class DocumentBuilder(namespace: String, id: String, schema: String) :
        GenericDocument.Builder<DocumentBuilder>(namespace, id, schema)

    private const val TIMEOUT_SECONDS = 10L
}
