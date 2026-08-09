package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.os.Bundle
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TabWebViewStateRepository private constructor(context: Context) {
    private val store = TabWebViewStateStore(context.applicationContext)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "tab-webview-state-io")
    }
    private val revisionLock = Any()
    private val tabRevisions = mutableMapOf<String, Long>()
    private var epoch = 0L

    fun load(tabId: String): Bundle? = runCatching {
        executor.submit<Bundle?> { store.load(tabId) }.get()
    }.getOrNull()

    fun save(tabId: String, state: Bundle) {
        val revision = nextRevision(tabId)
        executor.execute {
            if (isCurrent(tabId, revision)) store.save(tabId, state)
        }
    }

    fun delete(tabId: String) {
        val revision = nextRevision(tabId)
        executor.execute {
            if (isCurrent(tabId, revision)) store.delete(tabId)
        }
    }

    fun prune(validTabIds: Set<String>) {
        executor.execute { store.prune(validTabIds) }
    }

    fun clear() {
        synchronized(revisionLock) {
            epoch++
            tabRevisions.clear()
        }
        executor.execute(store::clear)
    }

    fun flush(): Boolean = runCatching {
        executor.submit {}.get()
        true
    }.getOrDefault(false)

    private fun nextRevision(tabId: String): Pair<Long, Long> = synchronized(revisionLock) {
        val revision = tabRevisions.getOrDefault(tabId, 0L) + 1L
        tabRevisions[tabId] = revision
        epoch to revision
    }

    private fun isCurrent(tabId: String, expected: Pair<Long, Long>): Boolean =
        synchronized(revisionLock) {
            epoch == expected.first && tabRevisions[tabId] == expected.second
        }

    companion object {
        @Volatile
        private var instance: TabWebViewStateRepository? = null

        fun get(context: Context): TabWebViewStateRepository = instance ?: synchronized(this) {
            instance ?: TabWebViewStateRepository(context).also { instance = it }
        }
    }
}
