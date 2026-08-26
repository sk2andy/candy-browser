package dev.sk2andy.materialbrowser.data

import android.content.Context
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.CandyTrail
import dev.sk2andy.materialbrowser.browser.CandyTrailRules
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal data class PendingCandyTrailRedaction(
    val id: String,
    val tabIds: Set<String>,
    val sinceInclusiveMillis: Long,
    val untilExclusiveMillis: Long,
)

class CandyTrailRepository private constructor(context: Context) {
    private val store = CandyTrailStore(context.applicationContext)
    private val sessionStore = BrowserSessionStore(context.applicationContext)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "candy-trail-io")
    }

    fun restore(
        tabs: List<BrowserTab>,
        retainedTabIds: Set<String> = emptySet(),
        onLoaded: (String, CandyTrail) -> Unit,
        onComplete: () -> Unit,
    ) {
        val persistentTabIds = CandyTrailPersistenceRules.persistentTabIds(tabs)
        executor.execute {
            store.prune(persistentTabIds + retainedTabIds)
            persistentTabIds.forEach { tabId ->
                store.load(tabId)?.let { trail -> onLoaded(tabId, trail) }
            }
            onComplete()
        }
    }

    fun save(tab: BrowserTab, trail: CandyTrail) {
        executor.execute {
            if (CandyTrailPersistenceRules.canPersist(tab)) {
                store.save(tab.id, trail)
            } else {
                store.delete(tab.id)
            }
        }
    }

    fun restoreTab(tabId: String, onLoaded: (CandyTrail) -> Unit) {
        executor.execute {
            store.load(tabId)?.let(onLoaded)
        }
    }

    fun delete(tabId: String) {
        executor.execute { store.delete(tabId) }
    }

    fun clear() {
        executor.execute(store::clear)
    }

    fun processPendingRedactions(acknowledge: Boolean = true) {
        executor.execute {
            sessionStore.loadPendingCandyTrailRedactions().forEach { redaction ->
                val complete = redaction.tabIds.all { tabId ->
                    val trail = store.load(tabId) ?: return@all true
                    val retained = CandyTrailRules.removeVisitedRange(
                        trail = trail,
                        sinceInclusiveMillis = redaction.sinceInclusiveMillis,
                        untilExclusiveMillis = redaction.untilExclusiveMillis,
                    )
                    when {
                        retained == trail -> true
                        retained.nodes.isEmpty() -> store.delete(tabId)
                        else -> store.save(tabId, retained)
                    }
                }
                if (complete && acknowledge) {
                    sessionStore.removePendingCandyTrailRedaction(redaction.id)
                }
            }
        }
    }

    fun flush(): Boolean = executor.awaitIdle()

    companion object {
        @Volatile
        private var instance: CandyTrailRepository? = null

        fun get(context: Context): CandyTrailRepository = instance ?: synchronized(this) {
            instance ?: CandyTrailRepository(context).also { instance = it }
        }
    }
}
