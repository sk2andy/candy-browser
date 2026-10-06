package dev.sk2andy.materialbrowser.browser

import android.util.Log
import dev.sk2andy.materialbrowser.BuildConfig

/** Opt-in debug traces contain runtime identities and counts, never page or input data. */
internal object BrowserSessionDiagnostics {
    private const val TAG = "CandySessions"

    enum class Event {
        Created,
        Selected,
        NavigationStarted,
        Foregrounded,
        Backgrounded,
        CapacityEviction,
        IdleEviction,
        BackgroundEviction,
        Closed,
        RendererTerminated,
    }

    fun record(
        event: Event,
        session: Any?,
        selected: Boolean,
        inBackground: Boolean,
        residentCount: Int,
        privateBrowsing: Boolean,
    ) {
        if (!BuildConfig.DEBUG || privateBrowsing || !Log.isLoggable(TAG, Log.VERBOSE)) return
        Log.v(
            TAG,
            "event=$event session=${session?.let(System::identityHashCode) ?: 0} " +
                "selected=$selected background=$inBackground residentCount=$residentCount",
        )
    }
}
