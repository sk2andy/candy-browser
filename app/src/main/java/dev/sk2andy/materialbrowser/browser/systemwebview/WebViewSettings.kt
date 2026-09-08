package dev.sk2andy.materialbrowser.browser.systemwebview

import android.webkit.WebSettings
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

internal fun WebSettings.enablePinchZoom() {
    setSupportZoom(true)
    builtInZoomControls = true
    displayZoomControls = false
}

internal fun WebSettings.allowContinuousMediaPlayback() {
    mediaPlaybackRequiresUserGesture = false
}

internal fun WebSettings.requireMediaPlaybackGesture() {
    mediaPlaybackRequiresUserGesture = true
}

internal fun WebSettings.applyWebsiteDarkeningPolicy(forceDarkWebsites: Boolean) {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(this, forceDarkWebsites)
    }
}
