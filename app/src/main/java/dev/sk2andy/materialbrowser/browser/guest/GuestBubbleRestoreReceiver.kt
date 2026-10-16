package dev.sk2andy.materialbrowser.browser.guest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restores owner widgets and their scheduler in the process that owns WorkManager. */
class GuestBubbleRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        GuestBubblePublicSurfaces.restore(context)
    }
}
