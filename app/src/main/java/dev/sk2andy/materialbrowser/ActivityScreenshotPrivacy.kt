package dev.sk2andy.materialbrowser

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import java.util.WeakHashMap

private val previousSecureFlags = WeakHashMap<Activity, Boolean>()

internal fun Activity.setCandyRecentsScreenshotEnabled(enabled: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        setRecentsScreenshotEnabled(enabled)
    } else if (enabled) {
        if (previousSecureFlags.remove(this) == false) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    } else {
        previousSecureFlags.getOrPut(this) {
            window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
