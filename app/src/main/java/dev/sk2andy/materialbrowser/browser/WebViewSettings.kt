package dev.sk2andy.materialbrowser.browser

import android.webkit.WebSettings

internal fun WebSettings.enablePinchZoom() {
    setSupportZoom(true)
    builtInZoomControls = true
    displayZoomControls = false
}

internal fun WebSettings.allowContinuousMediaPlayback() {
    // Modern players such as Reddit unmute asynchronously and issue play() after
    // the original click activation has expired. Requiring another gesture here
    // makes Chromium immediately pause otherwise valid user-started playback.
    mediaPlaybackRequiresUserGesture = false
}

internal fun WebSettings.requireMediaPlaybackGesture() {
    mediaPlaybackRequiresUserGesture = true
}
