package dev.sk2andy.materialbrowser.browser.engine

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Process
import android.os.SystemClock

/** Restarts Candy in a fresh process so only the selected browser runtime remains resident. */
internal object BrowserEngineProcessRestart {
    fun restart(activity: Activity) {
        val launchIntent = activity.packageManager
            .getLaunchIntentForPackage(activity.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ?: return activity.recreate()
        val pendingIntent = PendingIntent.getActivity(
            activity,
            REQUEST_CODE,
            launchIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        activity.getSystemService(AlarmManager::class.java)?.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + RESTART_DELAY_MILLIS,
            pendingIntent,
        ) ?: return activity.recreate()
        activity.finishAffinity()
        Process.killProcess(Process.myPid())
    }

    private const val REQUEST_CODE = 7_031
    private const val RESTART_DELAY_MILLIS = 250L
}
