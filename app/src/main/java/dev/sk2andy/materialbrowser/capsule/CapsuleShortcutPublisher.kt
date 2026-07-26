package dev.sk2andy.materialbrowser.capsule

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.content.pm.ShortcutManager
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import dev.sk2andy.materialbrowser.MainActivity

class CapsuleShortcutPublisher(private val context: Context) {
    fun isPinningSupported(): Boolean = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    fun requestPin(capsule: SiteCapsule, icon: Bitmap): Boolean = runCatching {
        ShortcutManagerCompat.requestPinShortcut(context, shortcut(capsule, icon), null)
    }.getOrDefault(false)

    fun update(capsule: SiteCapsule, icon: Bitmap): Boolean = runCatching {
        ShortcutManagerCompat.updateShortcuts(context, listOf(shortcut(capsule, icon)))
    }.getOrDefault(false)

    fun isPinned(capsule: SiteCapsule): Boolean = runCatching {
        val shortcutId = CapsuleShortcutRules.project(capsule).shortcutId
        context.getSystemService(ShortcutManager::class.java)
            .pinnedShortcuts
            .any { shortcut -> shortcut.id == shortcutId }
    }.getOrDefault(false)

    fun disable(capsule: SiteCapsule, disabledMessage: CharSequence) {
        runCatching {
            ShortcutManagerCompat.disableShortcuts(
                context,
                listOf(CapsuleShortcutRules.project(capsule).shortcutId),
                disabledMessage,
            )
        }
    }

    fun reportUsed(capsule: SiteCapsule) {
        runCatching {
            ShortcutManagerCompat.reportShortcutUsed(
                context,
                CapsuleShortcutRules.project(capsule).shortcutId,
            )
        }
    }

    internal fun launchIntent(capsuleId: String): Intent = Intent(context, MainActivity::class.java)
        .setAction(CapsuleIntentRules.ACTION_OPEN_CAPSULE)
        .putExtra(CapsuleIntentRules.EXTRA_CAPSULE_ID, capsuleId)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    private fun shortcut(capsule: SiteCapsule, icon: Bitmap): ShortcutInfoCompat {
        val projection = CapsuleShortcutRules.project(capsule)
        return ShortcutInfoCompat.Builder(context, projection.shortcutId)
            .setActivity(ComponentName(context, MainActivity::class.java))
            .setShortLabel(projection.shortLabel)
            .setLongLabel(projection.longLabel)
            .setIcon(IconCompat.createWithAdaptiveBitmap(icon))
            .setIntent(launchIntent(projection.capsuleId))
            .build()
    }
}
