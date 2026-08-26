package dev.sk2andy.materialbrowser

import android.app.Activity
import android.app.Application
import android.os.Bundle
import dev.sk2andy.materialbrowser.data.BrowsingHistoryRepository
import dev.sk2andy.materialbrowser.data.CandyTrailRepository

internal object BrowsingHistoryLifecycle {
    private var installed = false

    @Synchronized
    fun install(application: Application) {
        if (installed) return
        installed = true
        BrowsingHistoryRepository.get(application).beginSession()
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                private var startedActivityCount = 0

                override fun onActivityStarted(activity: Activity) {
                    if (startedActivityCount == 0) {
                        BrowsingHistoryRepository.get(application).markForegroundSessionActive()
                    }
                    startedActivityCount++
                }

                override fun onActivityStopped(activity: Activity) {
                    startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
                    if (startedActivityCount == 0 && !activity.isChangingConfigurations) {
                        if (BrowsingHistoryRepository.get(application).clearOnExit()) {
                            CandyTrailRepository.get(application)
                                .processPendingRedactions(acknowledge = false)
                        }
                    }
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }
}
