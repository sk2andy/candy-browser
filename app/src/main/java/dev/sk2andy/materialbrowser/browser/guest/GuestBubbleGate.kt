package dev.sk2andy.materialbrowser.browser.guest

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.WindowManager
import dev.sk2andy.materialbrowser.GuestBubbleActivity
import dev.sk2andy.materialbrowser.GuestBubbleCleanupActivity
import dev.sk2andy.materialbrowser.MainActivity
import java.util.WeakHashMap

/** Shields every owner task, including warm activities outside the main browser task. */
internal object GuestBubbleGate : Application.ActivityLifecycleCallbacks {
    private val ownerActivities = WeakHashMap<Activity, Boolean>()
    private val shieldedActivities = WeakHashMap<Activity, Boolean>()

    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    fun shieldOwnerActivities() {
        ownerActivities.keys.toList().forEach { activity ->
            (activity as? MainActivity)?.prepareForGuestBubble()
            shield(activity)
        }
    }

    fun redirectIfLocked(activity: Activity, finishOwner: Boolean = true): Boolean {
        if (activity is GuestBubbleActivity || activity is GuestBubbleCleanupActivity) return false
        val store = GuestBubbleSessionStore(activity)
        if (!store.isLocked()) return false
        shield(activity)
        val target = if (store.state()?.phase == GuestBubblePhase.Closing) {
            GuestBubbleCleanupActivity.createIntent(activity)
        } else {
            GuestBubbleActivity.createIntent(activity)
        }
        activity.startActivity(target)
        if (finishOwner) activity.finish()
        return true
    }

    fun restoreIfUnlocked(activity: Activity) {
        if (GuestBubbleSessionStore(activity).isLocked()) return
        val wasSecure = shieldedActivities.remove(activity) ?: return
        if (!wasSecure) activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        activity.setVisible(true)
        GuestBubblePublicSurfaces.restore(activity.applicationContext)
    }

    private fun shield(activity: Activity) {
        shieldedActivities.putIfAbsent(
            activity,
            activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0,
        )
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        activity.setRecentsScreenshotEnabled(false)
        activity.setVisible(false)
    }

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity !is GuestBubbleActivity && activity !is GuestBubbleCleanupActivity) {
            ownerActivities[activity] = true
            if (GuestBubbleSessionStore(activity).isLocked()) shield(activity)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity !is MainActivity && activity !is GuestBubbleActivity && activity !is GuestBubbleCleanupActivity) {
            if (!redirectIfLocked(activity)) restoreIfUnlocked(activity)
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        ownerActivities.remove(activity)
        shieldedActivities.remove(activity)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
