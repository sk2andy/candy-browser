package dev.sk2andy.materialbrowser

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.os.SystemClock

/** Relaunches the browser from a separate process after an engine change. */
class BrowserEngineRestartActivity : Activity() {
    private var relaunchStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(false)
    }

    override fun onResume() {
        super.onResume()
        if (relaunchStarted) return
        relaunchStarted = true
        val mainProcessId = intent.getIntExtra(EXTRA_MAIN_PROCESS_ID, INVALID_PROCESS_ID)
        if (mainProcessId > 0 && mainProcessId != Process.myPid()) {
            Process.killProcess(mainProcessId)
        }
        awaitMainProcessExit(
            mainProcessId = mainProcessId,
            deadlineUptimeMillis = SystemClock.uptimeMillis() + PROCESS_EXIT_TIMEOUT_MILLIS,
        )
    }

    private fun awaitMainProcessExit(mainProcessId: Int, deadlineUptimeMillis: Long) {
        val mainProcessRunning = mainProcessId > 0 &&
            getSystemService(ActivityManager::class.java).runningAppProcesses
                ?.any { process -> process.pid == mainProcessId } == true
        if (mainProcessRunning && SystemClock.uptimeMillis() < deadlineUptimeMillis) {
            window.decorView.postDelayed(
                { awaitMainProcessExit(mainProcessId, deadlineUptimeMillis) },
                PROCESS_EXIT_POLL_MILLIS,
            )
            return
        }
        startActivity(
            Intent.makeRestartActivityTask(ComponentName(this, MainActivity::class.java)),
        )
        finishAndRemoveTask()
        Process.killProcess(Process.myPid())
    }

    companion object {
        internal fun createIntent(context: Context, mainProcessId: Int): Intent =
            Intent(context, BrowserEngineRestartActivity::class.java)
                .putExtra(EXTRA_MAIN_PROCESS_ID, mainProcessId)

        private const val EXTRA_MAIN_PROCESS_ID = "main_process_id"
        private const val INVALID_PROCESS_ID = -1
        private const val PROCESS_EXIT_POLL_MILLIS = 16L
        private const val PROCESS_EXIT_TIMEOUT_MILLIS = 1_000L
    }
}
