package dev.sk2andy.materialbrowser.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.sk2andy.materialbrowser.browser.MAX_TABS

class SnoozeScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    fun schedule(tabs: List<SnoozedTab>, nowMillis: Long = System.currentTimeMillis()) {
        val operation = wakeOperation(appContext)
        alarmManager.cancel(operation)
        val nextWakeAt = SnoozeScheduleRules.nextTriggerAt(tabs, nowMillis)
        if (nextWakeAt == null) {
            Log.i(LOG_TAG, "Alarm cleared; pending=0")
            return
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextWakeAt, operation)
        Log.i(LOG_TAG, "Inexact alarm scheduled; pending=${tabs.size}; triggerAt=$nextWakeAt")
    }

    companion object {
        internal const val ACTION_WAKE = "dev.sk2andy.materialbrowser.action.WAKE_SNOOZED_TABS"
        private const val REQUEST_CODE = 50_071
        internal const val LOG_TAG = "CandySnooze"

        private fun wakeOperation(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, SnoozeAlarmReceiver::class.java).setAction(ACTION_WAKE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

internal object SnoozeScheduleRules {
    private const val OVERDUE_RETRY_DELAY_MILLIS = 15 * 60 * 1_000L

    fun nextTriggerAt(tabs: List<SnoozedTab>, nowMillis: Long): Long? {
        if (tabs.isEmpty()) return null
        val earliest = tabs.minOf(SnoozedTab::wakeAtMillis)
        return if (earliest > nowMillis) earliest else nowMillis + OVERDUE_RETRY_DELAY_MILLIS
    }
}

class SnoozeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (AppDataTransferLock.isActive(context)) return
        val supportedAction = intent.action == SnoozeScheduler.ACTION_WAKE ||
            intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED
        if (!supportedAction) return
        val nowMillis = System.currentTimeMillis()
        Log.i(SnoozeScheduler.LOG_TAG, "Receiver action=${intent.action}; now=$nowMillis")
        if (!SnoozeRuntimeRegistry.restoreDue(nowMillis)) {
            SnoozeRestoreCoordinator.restoreDue(context.applicationContext, nowMillis)
        }
        val pending = SnoozedTabStore(context).load()
        SnoozeScheduler(context).schedule(pending, nowMillis)
    }
}

internal object SnoozeRuntimeRegistry {
    @Volatile
    private var callback: ((Long) -> Unit)? = null

    fun register(callback: (Long) -> Unit) {
        this.callback = callback
    }

    fun unregister(callback: (Long) -> Unit) {
        if (this.callback === callback) this.callback = null
    }

    fun restoreDue(nowMillis: Long): Boolean {
        val activeCallback = callback ?: return false
        activeCallback(nowMillis)
        return true
    }
}

internal object SnoozeRestoreCoordinator {
    @Synchronized
    fun restoreDue(context: Context, nowMillis: Long) {
        val snoozedStore = SnoozedTabStore(context)
        val snoozed = snoozedStore.load()
        if (snoozed.none { it.wakeAtMillis <= nowMillis }) return
        val sessionStore = BrowserSessionStore(context)
        val (profiles, activeProfileId) = sessionStore.loadProfiles()
        val (tabs, selectedTabId) = sessionStore.loadTabs(nowMillis)
        val result = SnoozeRestoreRules.restoreDue(
            tabs = tabs,
            snoozedTabs = snoozed,
            profiles = profiles,
            activeProfileId = activeProfileId,
            nowMillis = nowMillis,
            maxTabs = MAX_TABS,
        )
        if (result.completedTabIds.isEmpty()) return
        val selection = selectedTabId?.takeIf { selected -> result.tabs.any { it.id == selected } }
            ?: result.tabs.firstOrNull { it.profileId == activeProfileId }?.id
            ?: result.tabs.firstOrNull()?.id
            ?: return
        val remaining = snoozed.filterNot { it.tab.id in result.completedTabIds }
        if (sessionStore.saveTabsAndSnoozedImmediately(result.tabs, selection, remaining)) {
            SnoozeWakeNotifier(context).notifyRestored(
                result.tabs.filter { it.id in result.restoredTabIds },
            )
            Log.i(
                SnoozeScheduler.LOG_TAG,
                "Background restore completed=${result.completedTabIds.size}",
            )
        }
    }
}
