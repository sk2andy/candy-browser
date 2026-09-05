@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package dev.sk2andy.materialbrowser.ui

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import dev.sk2andy.materialbrowser.R

internal fun View.performConfirmHaptic() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        },
    )
}

internal fun View.performRejectHaptic() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        },
    )
}

internal fun View.performTabFocusHaptic() {
    if (!performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)) {
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}

internal fun View.startRubberbandHaptic() {
    val vibrator = rubberbandVibrator() ?: return
    if (!vibrator.hasVibrator()) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val effect = if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(
                longArrayOf(0L, 1_000L),
                intArrayOf(0, 8),
                0,
            )
        } else {
            VibrationEffect.createWaveform(
                longArrayOf(0L, 3L, 117L),
                intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0),
                0,
            )
        }
        vibrator.vibrate(effect)
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(5_000L)
    }
}

internal fun View.stopRubberbandHaptic() {
    rubberbandVibrator()?.cancel()
}

private fun View.rubberbandVibrator(): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
