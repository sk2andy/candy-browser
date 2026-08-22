package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.util.Log
import dev.sk2andy.materialbrowser.browser.userscript.UserScript
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptDependencyResolution
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptDependencyResolver
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class UserScriptRepository private constructor(context: Context) {
    private val store = UserScriptStore(context.applicationContext)
    private val dependencyResolver = UserScriptDependencyResolver(UserScriptDependencyClient())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "userscript-io")
    }

    fun load(): List<UserScript> = runCatching {
        executor.submit<List<UserScript>>(store::load).get()
    }.getOrElse { error ->
        Log.e(TAG, "Userscript snapshot could not be loaded", error)
        emptyList()
    }

    fun save(scripts: List<UserScript>, onComplete: (Boolean) -> Unit) {
        val snapshot = scripts.toList()
        executor.execute {
            val saved = store.save(snapshot)
            if (!saved) Log.e(TAG, "Userscript snapshot could not be persisted")
            onComplete(saved)
        }
    }

    fun resolveDependencies(
        script: UserScript,
        onComplete: (UserScriptDependencyResolution) -> Unit,
    ) {
        executor.execute { onComplete(dependencyResolver.resolve(script)) }
    }

    fun clear() {
        executor.execute(store::clear)
    }

    fun flush(): Boolean = executor.awaitIdle()

    companion object {
        private const val TAG = "UserScriptRepository"

        @Volatile
        private var instance: UserScriptRepository? = null

        fun get(context: Context): UserScriptRepository = instance ?: synchronized(this) {
            instance ?: UserScriptRepository(context.applicationContext).also { instance = it }
        }
    }
}
