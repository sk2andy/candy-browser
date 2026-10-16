package dev.sk2andy.materialbrowser

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.FileObserver
import android.view.View
import dev.sk2andy.materialbrowser.browser.guest.GuestBubbleGate
import dev.sk2andy.materialbrowser.capsule.CapsuleIntentRules
import dev.sk2andy.materialbrowser.capsule.SiteCapsuleRules
import dev.sk2andy.materialbrowser.capsule.CapsuleWindowRules
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.SiteCapsuleIconStore
import dev.sk2andy.materialbrowser.data.SiteCapsuleStore

/** One Android document task per Capsule; browser tabs use a separate session snapshot. */
class SiteCapsuleActivity : MainActivity() {
    private var ownerProfileId: String? = null
    private var capsuleObserver: FileObserver? = null
    private val profilePreferences by lazy {
        getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    private val profileListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == BrowserSessionStore.KEY_PROFILES) {
            runOnUiThread { validateWindow() }
        }
    }

    override val capsuleWindowId: String?
        get() = SiteCapsuleRules.opaqueId(intent.getStringExtra(CapsuleIntentRules.EXTRA_CAPSULE_ID))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (GuestBubbleGate.redirectIfLocked(this)) return
        val capsule = SiteCapsuleStore(this).load().firstOrNull { it.id == capsuleWindowId }
        if (capsule == null || !CapsuleWindowRules.canOpen(capsule, BrowserSessionStore(this).loadProfiles().first)) {
            discardCapsuleWindowSession()
            finishAndRemoveTask()
            return
        }
        ownerProfileId = capsule.profileId
        profilePreferences.registerOnSharedPreferenceChangeListener(profileListener)
        capsuleObserver = object : FileObserver(
            filesDir,
            FileObserver.MOVED_TO or FileObserver.CLOSE_WRITE or FileObserver.DELETE,
        ) {
            override fun onEvent(event: Int, path: String?) {
                if (path == "site_capsules_v1.json") runOnUiThread { validateWindow() }
                if (path == "candy_filter_rules.json") {
                    runOnUiThread { refreshCapsuleWindowFilterRules() }
                }
            }
        }.also(FileObserver::startWatching)
        @Suppress("DEPRECATION")
        setTaskDescription(
            ActivityManager.TaskDescription(
                capsule.name,
                SiteCapsuleIconStore(this).load(capsule.id),
                capsule.iconColor.backgroundArgb.toInt(),
            ),
        )
    }

    override fun onResume() {
        if (ownerProfileId != null) validateWindow()
        super.onResume()
    }

    override fun onDestroy() {
        profilePreferences.unregisterOnSharedPreferenceChangeListener(profileListener)
        capsuleObserver?.stopWatching()
        capsuleObserver = null
        super.onDestroy()
    }

    private fun validateWindow() {
        if (isFinishing || isDestroyed) return
        val capsule = SiteCapsuleStore(this).load().firstOrNull { it.id == capsuleWindowId }
        val profiles = BrowserSessionStore(this).loadProfiles().first
        if (capsule == null || capsule.profileId != ownerProfileId || !CapsuleWindowRules.canOpen(capsule, profiles)) {
            window.decorView.visibility = View.INVISIBLE
            discardCapsuleWindowSession()
            finishAndRemoveTask()
        }
    }
}
