package dev.sk2andy.materialbrowser

import android.app.Activity
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

internal fun Activity.applyFullImmersiveMode(
    enabled: Boolean,
    keepWindowFullHeightForIme: Boolean = false,
) {
    val decorView = window.decorView
    if (enabled && keepWindowFullHeightForIme) {
        if (decorView.getTag(R.id.full_immersive_previous_soft_input_mode) == null) {
            decorView.setTag(
                R.id.full_immersive_previous_soft_input_mode,
                window.attributes.softInputMode,
            )
        }
        val softInputMode = window.attributes.softInputMode
        window.setSoftInputMode(
            softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv() or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING,
        )
    } else if (!enabled) {
        val previousSoftInputMode =
            decorView.getTag(R.id.full_immersive_previous_soft_input_mode) as? Int
        if (previousSoftInputMode != null) {
            window.setSoftInputMode(previousSoftInputMode)
            decorView.setTag(R.id.full_immersive_previous_soft_input_mode, null)
        }
    }

    WindowCompat.getInsetsController(window, decorView).apply {
        if (enabled) {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        } else {
            show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
