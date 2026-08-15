package dev.sk2andy.materialbrowser

import androidx.activity.ComponentActivity
import androidx.core.view.WindowInsetsControllerCompat

internal fun ComponentActivity.applyAppearanceSystemBars(dark: Boolean) {
    WindowInsetsControllerCompat(window, window.decorView).apply {
        isAppearanceLightStatusBars = !dark
        isAppearanceLightNavigationBars = !dark
    }
}
