package dev.sk2andy.materialbrowser.browser.guest

import android.app.Activity
import dev.sk2andy.materialbrowser.data.AppDataTransferLock
import dev.sk2andy.materialbrowser.GuestBubbleActivity
import dev.sk2andy.materialbrowser.data.BrowserSessionStore

internal object GuestBubbleLauncher {
    fun start(activity: Activity): Boolean {
        if (activity.isFinishing || AppDataTransferLock.isActive(activity) ||
            !GuestBubbleAuthenticator.isAvailable(activity) ||
            !BrowserSessionStore(activity).loadProfilesEnabled()
        ) return false
        val store = GuestBubbleSessionStore(activity)
        if (!store.isEnabled() || !store.start()) return false
        GuestBubbleGate.shieldOwnerActivities()
        activity.startActivity(GuestBubbleActivity.createIntent(activity))
        return true
    }
}
