package dev.sk2andy.materialbrowser.ui

import kotlin.math.PI
import kotlin.math.sin

internal object CandyCircuitMotionRules {
    const val RESOLUTION_DURATION_MILLIS = 1_250

    fun closingRotationProgress(progress: Float): Float = segment(progress, 0f, ROTATION_END)

    fun outgoingAlpha(progress: Float): Float =
        1f - segment(progress, EXIT_START, EXIT_END)

    fun outgoingScale(progress: Float): Float {
        val pulse = segment(progress, 0f, EXIT_START)
        val exit = segment(progress, EXIT_START, EXIT_END)
        return (1f + sin(pulse * PI).toFloat() * PULSE_SCALE) * (1f - exit * EXIT_SHRINK)
    }

    fun glowAlpha(progress: Float): Float {
        val pulse = segment(progress, 0f, EXIT_START)
        val exit = segment(progress, EXIT_START, EXIT_END)
        return (sin(pulse * PI).toFloat() * (1f - exit)).coerceIn(0f, 1f)
    }

    fun particleProgress(progress: Float): Float = segment(progress, PARTICLE_START, PARTICLE_END)

    fun incomingProgress(
        progress: Float,
        order: Int,
        count: Int,
    ): Float {
        val safeCount = count.coerceAtLeast(1)
        val safeOrder = order.coerceIn(0, safeCount - 1)
        val stagger = safeOrder.toFloat() / safeCount * ENTRY_STAGGER_WINDOW
        return segment(progress, ENTRY_START + stagger, ENTRY_END)
    }

    fun incomingScale(progress: Float): Float {
        val clamped = progress.coerceIn(0f, 1f)
        val base = ENTRY_INITIAL_SCALE + (1f - ENTRY_INITIAL_SCALE) * clamped
        val bounce = sin(clamped * PI).toFloat() * ENTRY_OVERSHOOT
        return base + bounce
    }

    fun incomingOffsetFraction(progress: Float): Float {
        val remaining = 1f - progress.coerceIn(0f, 1f)
        return -ENTRY_TRAVEL * remaining * remaining
    }

    private fun segment(
        progress: Float,
        start: Float,
        end: Float,
    ): Float = ((progress.coerceIn(0f, 1f) - start) / (end - start)).coerceIn(0f, 1f)

    private const val EXIT_START = 0.30f
    private const val EXIT_END = 0.56f
    private const val PARTICLE_START = 0.26f
    private const val PARTICLE_END = 0.68f
    private const val ENTRY_START = 0.48f
    private const val ENTRY_END = 0.96f
    private const val ENTRY_STAGGER_WINDOW = 0.20f
    private const val PULSE_SCALE = 0.10f
    private const val EXIT_SHRINK = 0.32f
    private const val ENTRY_INITIAL_SCALE = 0.62f
    private const val ENTRY_OVERSHOOT = 0.24f
    private const val ENTRY_TRAVEL = 0.82f
    private const val ROTATION_END = 0.20f
}
