package dev.sk2andy.materialbrowser.data

data class GeckoSafeAreaSettings(
    val enabled: Boolean = true,
    val recheckAddedElements: Boolean = true,
    val recheckChangedElements: Boolean = true,
    val requireInteractionForUpdates: Boolean = true,
    val recheckOnResize: Boolean = true,
    val interactionWindowMillis: Int = DEFAULT_INTERACTION_WINDOW_MILLIS,
    val mutationDebounceMillis: Int = DEFAULT_MUTATION_DEBOUNCE_MILLIS,
    val maxElementsPerBatch: Int = DEFAULT_MAX_ELEMENTS_PER_BATCH,
    val maxBatchDurationMillis: Int = DEFAULT_MAX_BATCH_DURATION_MILLIS,
    val maxInitialElements: Int = DEFAULT_MAX_INITIAL_ELEMENTS,
) {
    val hasDefaultSettings: Boolean
        get() = this == GeckoSafeAreaSettings()

    fun withDefaults(): GeckoSafeAreaSettings = GeckoSafeAreaSettings()

    fun normalized(): GeckoSafeAreaSettings = copy(
        interactionWindowMillis = interactionWindowMillis.normalizedStep(
            MIN_INTERACTION_WINDOW_MILLIS..MAX_INTERACTION_WINDOW_MILLIS,
            INTERACTION_WINDOW_STEP_MILLIS,
        ),
        mutationDebounceMillis = mutationDebounceMillis.normalizedStep(
            MIN_MUTATION_DEBOUNCE_MILLIS..MAX_MUTATION_DEBOUNCE_MILLIS,
            MUTATION_DEBOUNCE_STEP_MILLIS,
        ),
        maxElementsPerBatch = maxElementsPerBatch.normalizedStep(
            MIN_MAX_ELEMENTS_PER_BATCH..MAX_MAX_ELEMENTS_PER_BATCH,
            MAX_ELEMENTS_PER_BATCH_STEP,
        ),
        maxBatchDurationMillis = maxBatchDurationMillis.normalizedStep(
            MIN_MAX_BATCH_DURATION_MILLIS..MAX_MAX_BATCH_DURATION_MILLIS,
            MAX_BATCH_DURATION_STEP_MILLIS,
        ),
        maxInitialElements = maxInitialElements.normalizedStep(
            MIN_MAX_INITIAL_ELEMENTS..MAX_MAX_INITIAL_ELEMENTS,
            MAX_INITIAL_ELEMENTS_STEP,
        ),
    )

    private fun Int.normalizedStep(range: IntRange, step: Int): Int {
        val bounded = coerceIn(range)
        val offset = bounded - range.first
        val roundedSteps = (offset + step / 2) / step
        return range.first + roundedSteps * step
    }

    companion object {
        const val DEFAULT_INTERACTION_WINDOW_MILLIS = 1_000
        const val MIN_INTERACTION_WINDOW_MILLIS = 100
        const val MAX_INTERACTION_WINDOW_MILLIS = 5_000
        const val INTERACTION_WINDOW_STEP_MILLIS = 100
        const val DEFAULT_MUTATION_DEBOUNCE_MILLIS = 150
        const val MIN_MUTATION_DEBOUNCE_MILLIS = 50
        const val MAX_MUTATION_DEBOUNCE_MILLIS = 1_000
        const val MUTATION_DEBOUNCE_STEP_MILLIS = 50
        const val DEFAULT_MAX_ELEMENTS_PER_BATCH = 16
        const val MIN_MAX_ELEMENTS_PER_BATCH = 4
        const val MAX_MAX_ELEMENTS_PER_BATCH = 64
        const val MAX_ELEMENTS_PER_BATCH_STEP = 4
        const val DEFAULT_MAX_BATCH_DURATION_MILLIS = 4
        const val MIN_MAX_BATCH_DURATION_MILLIS = 1
        const val MAX_MAX_BATCH_DURATION_MILLIS = 8
        const val MAX_BATCH_DURATION_STEP_MILLIS = 1
        const val DEFAULT_MAX_INITIAL_ELEMENTS = 512
        const val MIN_MAX_INITIAL_ELEMENTS = 64
        const val MAX_MAX_INITIAL_ELEMENTS = 2_048
        const val MAX_INITIAL_ELEMENTS_STEP = 64
    }
}
