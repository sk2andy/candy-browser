package dev.sk2andy.materialbrowser.ui

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import kotlin.math.roundToInt

internal class FullscreenVideoSystemControls(
    private val activity: Activity,
) {
    private val audioManager = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var fullscreenActive = false
    private var brightnessApplied = false
    private var originalWindowBrightness: Float? = null
    private var rememberedFullscreenBrightness: Float? = null

    fun setFullscreenActive(active: Boolean) {
        if (fullscreenActive == active) return
        fullscreenActive = active
        if (active) {
            applyFullscreenBrightness()
        } else {
            restoreWindowBrightness()
        }
    }

    fun onAppPaused() {
        restoreWindowBrightness()
    }

    fun onAppResumed() {
        if (fullscreenActive) applyFullscreenBrightness()
    }

    fun currentBrightnessFraction(): Float = rememberedFullscreenBrightness
        ?: currentDeviceBrightnessFraction()

    fun setBrightnessFraction(fraction: Float): Float {
        val bounded = fraction.coerceIn(0f, 1f)
        rememberedFullscreenBrightness = bounded
        if (fullscreenActive) {
            rememberOriginalWindowBrightness()
            activity.window.attributes = activity.window.attributes.apply {
                screenBrightness = bounded
            }
            brightnessApplied = true
        }
        return bounded
    }

    fun currentMediaVolumeFraction(): Float {
        val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (maximum <= 0) return 0f
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maximum
    }

    fun setMediaVolumeFraction(fraction: Float): Float {
        val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (maximum <= 0) return 0f
        val level = (fraction.coerceIn(0f, 1f) * maximum).roundToInt().coerceIn(0, maximum)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)
        return level.toFloat() / maximum
    }

    fun close() {
        fullscreenActive = false
        restoreWindowBrightness()
    }

    private fun applyFullscreenBrightness() {
        val remembered = rememberedFullscreenBrightness ?: return
        rememberOriginalWindowBrightness()
        activity.window.attributes = activity.window.attributes.apply {
            screenBrightness = remembered
        }
        brightnessApplied = true
    }

    private fun rememberOriginalWindowBrightness() {
        if (originalWindowBrightness == null) {
            originalWindowBrightness = activity.window.attributes.screenBrightness
        }
    }

    private fun restoreWindowBrightness() {
        val original = originalWindowBrightness ?: return
        if (brightnessApplied) {
            activity.window.attributes = activity.window.attributes.apply {
                screenBrightness = original
            }
        }
        originalWindowBrightness = null
        brightnessApplied = false
    }

    private fun currentDeviceBrightnessFraction(): Float {
        val windowBrightness = activity.window.attributes.screenBrightness
        if (windowBrightness >= 0f) return windowBrightness.coerceIn(0f, 1f)
        return runCatching {
            Settings.System.getInt(
                activity.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
            ).toFloat() / MAX_SYSTEM_BRIGHTNESS
        }.getOrDefault(DEFAULT_BRIGHTNESS_FRACTION).coerceIn(0f, 1f)
    }

    private companion object {
        const val MAX_SYSTEM_BRIGHTNESS = 255f
        const val DEFAULT_BRIGHTNESS_FRACTION = 0.5f
    }
}
