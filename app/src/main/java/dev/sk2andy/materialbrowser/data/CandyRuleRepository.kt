package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.util.Log
import dev.sk2andy.materialbrowser.blocking.CandyRule
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CandyRuleRepository private constructor(context: Context) {
    private val store = CandyRuleStore(context.applicationContext)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "candy-filter-io")
    }

    fun load(): List<CandyRule> = store.load()

    fun save(rules: List<CandyRule>) {
        val snapshot = rules.toList()
        executor.execute {
            if (!store.save(snapshot)) Log.e(TAG, "Candy rule snapshot could not be persisted")
        }
    }

    fun clear() {
        executor.execute(store::clear)
    }

    fun flush(): Boolean = executor.awaitIdle()

    companion object {
        private const val TAG = "CandyRuleRepository"
        @Volatile
        private var instance: CandyRuleRepository? = null

        fun get(context: Context): CandyRuleRepository = instance ?: synchronized(this) {
            instance ?: CandyRuleRepository(context).also { instance = it }
        }
    }
}
