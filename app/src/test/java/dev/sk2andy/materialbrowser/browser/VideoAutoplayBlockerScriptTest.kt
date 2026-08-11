package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertTrue
import org.junit.Test

class VideoAutoplayBlockerScriptTest {
    @Test
    fun installBlocksAutoplayAttributesAndProgrammaticVideoPlayback() {
        val script = VideoAutoplayBlockerScript.installScript

        assertTrue(script.contains("MutationObserver"))
        assertTrue(script.contains("video[autoplay]"))
        assertTrue(script.contains("this instanceof videoType"))
        assertTrue(script.contains("NotAllowedError"))
        assertTrue(script.contains("return originalPlay.apply(this, args)"))
    }

    @Test
    fun installAllowsOnlyPlaybackDuringActiveUserGestureAndCanCleanUp() {
        val script = VideoAutoplayBlockerScript.installScript

        assertTrue(script.contains("event.isTrusted"))
        assertTrue(script.contains("navigator.userActivation?.isActive"))
        assertTrue(script.contains("activationGeneration !== 0"))
        assertTrue(script.contains("queueMicrotask"))
        assertTrue(script.contains("playbackGrants.set(video"))
        assertTrue(script.contains("playbackGrants.delete(video)"))
        assertTrue(script.contains("event.type === 'pointerdown'"))
        assertTrue(script.contains("/(player|video|media)/i"))
        assertTrue(!script.contains("hasBeenActive"))
        assertTrue(!script.contains("unlockAfterUserGesture"))
        assertTrue(script.contains("removeEventListener('play'"))
        assertTrue(script.contains("mutationObserver.disconnect()"))
        assertTrue(script.contains("index < window.frames.length"))
        assertTrue(script.contains("window.frames[index].postMessage"))
        assertTrue(VideoAutoplayBlockerScript.cleanupScript.contains("?.()"))
    }
}
