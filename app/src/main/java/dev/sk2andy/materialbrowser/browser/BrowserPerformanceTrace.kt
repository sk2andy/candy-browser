package dev.sk2andy.materialbrowser.browser

import android.os.Trace
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.browser.gecko.GeckoPerformanceDiagnostics
import java.util.IdentityHashMap

/** Fixed labels keep URLs, page text and tab identities out of system traces. */
internal object BrowserPerformanceTrace {
    enum class Phase(val label: String) {
        DiagnosticsStart("Candy.Diagnostics.Start"),
        DiagnosticsStop("Candy.Diagnostics.Stop"),
        DiagnosticsGap("Candy.Diagnostics.UserObservedGap"),
        BlurBind("Candy.Blur.Bind"),
        BlurConfigure("Candy.Blur.Configure"),
        BlurDraw("Candy.Blur.Draw"),
        BlurHardwareDraw("Candy.Blur.HardwareDraw"),
        BlurSoftwareDraw("Candy.Blur.SoftwareDraw"),
        BlurPreDraw("Candy.Blur.PreDrawObserved"),
        BlurCapture("Candy.Blur.Capture"),
        BlurHardwareCapture("Candy.Blur.HardwareCapture"),
        BlurSourceChildDraw("Candy.Blur.SourceChildDraw"),
        BlurCaptureUnpublishedSource("Candy.Blur.CaptureUnpublishedSource"),
        BlurReleasedControllerDraw("Candy.Blur.ReleasedControllerDraw"),
        BlurRelease("Candy.Blur.Release"),
        GeckoTouch("Candy.Gecko.TouchDispatch"),
        GeckoScroll("Candy.Gecko.ScrollDispatch"),
        GeckoDocumentMetrics("Candy.Gecko.DocumentMetrics"),
        GeckoBackendSwitch("Candy.Gecko.BackendSwitch"),
        GeckoInsets("Candy.Gecko.Insets"),
        GeckoFirstComposite("Candy.Gecko.FirstComposite"),
        GeckoFirstContentfulPaint("Candy.Gecko.FirstContentfulPaint"),
        GeckoPaintReset("Candy.Gecko.PaintReset"),
        GeckoMediaPlay("Candy.Gecko.Media.Play"),
        GeckoMediaPause("Candy.Gecko.Media.Pause"),
        GeckoMediaStop("Candy.Gecko.Media.Stop"),
    }

    enum class Counter(val label: String) {
        BlurStateCoverage("Candy.Blur.StateCoverage"),
        BlurViews("Candy.Blur.ViewCount"),
        BlurEnabledViews("Candy.Blur.EnabledViewCount"),
        BlurSources("Candy.Blur.SourceCount"),
        BlurEnabledSources("Candy.Blur.EnabledSourceCount"),
        BlurConfigurationCoverage("Candy.Blur.ConfigurationCoverage"),
        BlurConfigurations("Candy.Blur.ConfigurationCount"),
        BlurRequestedConfigurations("Candy.Blur.RequestedConfigurationCount"),
    }

    private val blurState = BrowserBlurTraceState()

    fun sourceDrawPhase(hardwareAccelerated: Boolean): Phase =
        if (hardwareAccelerated) Phase.BlurCapture else Phase.BlurSourceChildDraw

    fun updateBlurParticipant(identity: Any, kind: BrowserBlurTraceState.Kind, enabled: Boolean) {
        if (!BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS) return
        blurState.update(identity, kind, enabled)
        reportBlurState()
    }

    fun removeBlurParticipant(identity: Any) {
        if (!BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS) return
        blurState.remove(identity)
        reportBlurState()
    }

    /** Counts include every attached participant, never a last-writer surface boolean. */
    private fun reportBlurState() {
        if (!BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS ||
            !GeckoPerformanceDiagnostics.isRecording || !Trace.isEnabled()
        ) return
        val counts = blurState.counts()
        Trace.setCounter(Counter.BlurStateCoverage.label, 1L)
        Trace.setCounter(Counter.BlurViews.label, counts.views.toLong())
        Trace.setCounter(Counter.BlurEnabledViews.label, counts.enabledViews.toLong())
        Trace.setCounter(Counter.BlurSources.label, counts.sources.toLong())
        Trace.setCounter(Counter.BlurEnabledSources.label, counts.enabledSources.toLong())
        Trace.setCounter(Counter.BlurConfigurationCoverage.label, if (counts.configurations > 0) 1L else 0L)
        Trace.setCounter(Counter.BlurConfigurations.label, counts.configurations.toLong())
        Trace.setCounter(Counter.BlurRequestedConfigurations.label, counts.requestedConfigurations.toLong())
    }

    fun begin(phase: Phase): Boolean {
        if (!BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS ||
            !GeckoPerformanceDiagnostics.isRecording || !Trace.isEnabled()
        ) return false
        when (phase) {
            Phase.DiagnosticsStart, Phase.DiagnosticsGap, Phase.DiagnosticsStop,
            Phase.BlurDraw, Phase.BlurCapture -> reportBlurState()
            else -> Unit
        }
        Trace.beginSection(phase.label)
        return true
    }

    inline fun <T> section(phase: Phase, block: () -> T): T {
        val started = begin(phase)
        return try {
            block()
        } finally {
            if (started) Trace.endSection()
        }
    }

    fun event(phase: Phase) {
        if (begin(phase)) Trace.endSection()
    }
}

/** Android-free participant accounting; identity survives equal-looking simultaneous surfaces. */
internal class BrowserBlurTraceState {
    enum class Kind { View, Source, Configuration }

    data class Counts(
        val views: Int,
        val enabledViews: Int,
        val sources: Int,
        val enabledSources: Int,
        val configurations: Int = 0,
        val requestedConfigurations: Int = 0,
    )

    private data class Participant(val kind: Kind, val enabled: Boolean)
    private val participants = IdentityHashMap<Any, Participant>()

    @Synchronized
    fun update(identity: Any, kind: Kind, enabled: Boolean) {
        participants[identity] = Participant(kind, enabled)
    }

    @Synchronized
    fun remove(identity: Any) {
        participants.remove(identity)
    }

    @Synchronized
    fun counts(): Counts = Counts(
        views = participants.values.count { it.kind == Kind.View },
        enabledViews = participants.values.count { it.kind == Kind.View && it.enabled },
        sources = participants.values.count { it.kind == Kind.Source },
        enabledSources = participants.values.count { it.kind == Kind.Source && it.enabled },
        configurations = participants.values.count { it.kind == Kind.Configuration },
        requestedConfigurations = participants.values.count { it.kind == Kind.Configuration && it.enabled },
    )
}
