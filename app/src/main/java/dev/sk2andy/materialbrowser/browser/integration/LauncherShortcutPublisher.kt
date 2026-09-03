package dev.sk2andy.materialbrowser.browser.integration

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import dev.sk2andy.materialbrowser.LauncherShortcutActivity
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class LauncherShortcutPublisher(private val context: Context) {
    suspend fun publishSerially(state: LauncherShortcutState): Boolean =
        PUBLISH_MUTEX.withLock {
            withContext(Dispatchers.IO) { publish(state) }
        }

    fun publish(state: LauncherShortcutState): Boolean = runCatching {
        val maximumShortcutCount = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
        val dynamicShortcutCount = (maximumShortcutCount - STATIC_SHORTCUT_COUNT)
            .coerceIn(0, MAX_DYNAMIC_PROFILE_SHORTCUTS)
        val shortcuts = state.recentProfiles
            .take(dynamicShortcutCount)
            .mapIndexed(::profileShortcut)
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }.getOrDefault(false)

    fun reportUsed(target: LauncherShortcutTarget) {
        runCatching {
            ShortcutManagerCompat.reportShortcutUsed(
                context,
                LauncherShortcutRules.shortcutId(target),
            )
        }
    }

    internal fun launchIntent(target: LauncherShortcutTarget): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(
                when (target) {
                    LauncherShortcutTarget.NewTab -> LauncherShortcutRules.ACTION_NEW_TAB
                    LauncherShortcutTarget.NewPrivateTab ->
                        LauncherShortcutRules.ACTION_NEW_PRIVATE_TAB
                    is LauncherShortcutTarget.Profile -> LauncherShortcutRules.ACTION_OPEN_PROFILE
                },
            )
            .apply {
                if (target is LauncherShortcutTarget.Profile) {
                    putExtra(LauncherShortcutRules.EXTRA_PROFILE_ID, target.profileId)
                }
            }
            .addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )

    private fun profileShortcut(
        rank: Int,
        profile: LauncherProfileShortcut,
    ): ShortcutInfoCompat = ShortcutInfoCompat.Builder(
        context,
        LauncherShortcutRules.shortcutId(LauncherShortcutTarget.Profile(profile.profileId)),
    )
        .setActivity(ComponentName(context, MainActivity::class.java))
        .setShortLabel(context.getString(R.string.command_target_profile, profile.emoji))
        .setLongLabel(context.getString(R.string.command_target_profile, profile.emoji))
        .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_shortcut_profile))
        .setIntent(dispatcherIntent(LauncherShortcutTarget.Profile(profile.profileId)))
        .setRank(rank)
        .build()

    private fun dispatcherIntent(target: LauncherShortcutTarget.Profile): Intent =
        Intent(context, LauncherShortcutActivity::class.java)
            .setAction(LauncherShortcutRules.ACTION_OPEN_PROFILE)
            .putExtra(LauncherShortcutRules.EXTRA_PROFILE_ID, target.profileId)

    private companion object {
        const val STATIC_SHORTCUT_COUNT = 2
        const val MAX_DYNAMIC_PROFILE_SHORTCUTS = 2
        val PUBLISH_MUTEX = Mutex()
    }
}
