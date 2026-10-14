package dev.sk2andy.materialbrowser

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.core.view.WindowInsetsControllerCompat

internal fun ComponentActivity.applyAppearanceSystemBars(
    dark: Boolean,
    statusBarUsesDarkIcons: Boolean = !dark,
) {
    WindowInsetsControllerCompat(window, window.decorView).apply {
        isAppearanceLightStatusBars = statusBarUsesDarkIcons
        isAppearanceLightNavigationBars = !dark
    }
}

internal fun View.applyStatusBarIconAppearance(useDarkIcons: Boolean) {
    val activity = context.findActivity() ?: return
    WindowInsetsControllerCompat(
        activity.window,
        activity.window.decorView,
    ).isAppearanceLightStatusBars = useDarkIcons
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
