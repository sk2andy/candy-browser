package dev.sk2andy.materialbrowser.constellations

import android.os.Handler

/** A freshly attached renderer may commit before its first readable layout. */
internal object ConstellationPageExtraction {
    fun request(
        handler: Handler,
        isCurrent: () -> Boolean,
        extract: ((String?) -> Unit) -> Unit,
        onComplete: (String) -> Unit,
    ) {
        fun attempt(ordinal: Int) {
            if (!isCurrent()) return
            extract { payload ->
                if (isCurrent()) {
                    if (payload != null) {
                        onComplete(payload)
                    } else if (ordinal < MAX_ATTEMPTS) {
                        handler.postDelayed({ attempt(ordinal + 1) }, RETRY_DELAY_MILLIS)
                    }
                }
            }
        }
        attempt(1)
    }

    private const val MAX_ATTEMPTS = 3
    private const val RETRY_DELAY_MILLIS = 250L
}
