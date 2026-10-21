package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowConfiguration
import java.util.concurrent.Executors

internal class CustomWorkflowRepository private constructor(context: Context) {
    private val store = CustomWorkflowStore(context.applicationContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "custom-workflow-io") }

    fun load(onLoaded: (CustomWorkflowConfiguration) -> Unit) {
        executor.execute {
            val configuration = store.load()
            mainHandler.post { onLoaded(configuration) }
        }
    }

    fun save(configuration: CustomWorkflowConfiguration, onSaved: (Boolean) -> Unit) {
        val snapshot = configuration.copy(
            workflows = configuration.workflows.map { it.copy(steps = it.steps.toList()) },
            bindings = configuration.bindings.toList(),
        )
        executor.execute {
            val saved = store.save(snapshot)
            mainHandler.post { onSaved(saved) }
        }
    }

    fun awaitIdle(onIdle: () -> Unit) {
        executor.execute { mainHandler.post { onIdle() } }
    }

    companion object {
        @Volatile
        private var instance: CustomWorkflowRepository? = null

        fun get(context: Context): CustomWorkflowRepository = instance ?: synchronized(this) {
            instance ?: CustomWorkflowRepository(context.applicationContext).also { instance = it }
        }
    }
}
