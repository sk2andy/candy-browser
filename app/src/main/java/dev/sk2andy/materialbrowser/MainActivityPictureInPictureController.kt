package dev.sk2andy.materialbrowser

import android.app.PictureInPictureParams
import android.app.PictureInPictureUiState
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.util.Rational
import android.view.View
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.FullscreenVideoBounds
import dev.sk2andy.materialbrowser.browser.FullscreenVideoRules

internal class MainActivityPictureInPictureController(
    private val activity: MainActivity,
    private val browserController: BrowserController,
    private val isVideoOnlyPresentation: () -> Boolean,
    private val setVideoOnlyPresentation: (Boolean) -> Unit,
    private val applyBrowserSystemUi: () -> Unit,
) {
    private var fullscreenVideoBounds: Rect? = null
    private var sourceRectHint: Rect? = null
    private var appliedState: AppliedPictureInPictureState? = null
    private var returnLayoutListener: View.OnLayoutChangeListener? = null
    private var returnInProgress = false
    private var startedFullscreen = false
    private var modeEntered = false

    fun requestPictureInPicture(): Boolean {
        if (!canEnterPictureInPicture()) return false
        prepareForTransition()
        val entered = activity.enterPictureInPictureMode(
            buildParams(
                autoEnterEnabled = true,
                sourceRectHint = eligibleSourceRect(true),
            ),
        )
        if (!entered) cancelTransition()
        return entered
    }

    fun onModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        if (isInPictureInPictureMode) {
            modeEntered = true
            setVideoOnlyPresentation(true)
        }
        browserController.onPictureInPictureModeChanged(isInPictureInPictureMode)
        if (isInPictureInPictureMode) {
            returnInProgress = false
            cancelReturnLayoutWait()
            if (startedFullscreen) {
                sourceRectHint = pictureInPictureSourceRect(maximumWindowContentBounds())
            }
        } else {
            returnInProgress = true
            completeReturnAfterLayout(newConfig)
        }
        applyBrowserSystemUi()
        updateParams()
    }

    fun onUiStateChanged(pipState: PictureInPictureUiState) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
            pipState.isTransitioningToPip
        ) {
            prepareForTransition()
        }
    }

    fun onConfigurationChanged() {
        if (activity.isInPictureInPictureMode && startedFullscreen) {
            sourceRectHint = pictureInPictureSourceRect(maximumWindowContentBounds())
            updateParams()
        }
    }

    fun onDestroy() {
        cancelReturnLayoutWait()
    }

    fun onFullscreenVideoBoundsChanged(bounds: Rect) {
        if (fullscreenVideoBounds == bounds) return
        fullscreenVideoBounds = Rect(bounds)
        if (activity.isInPictureInPictureMode && startedFullscreen) {
            sourceRectHint = pictureInPictureSourceRect(maximumWindowContentBounds())
        }
        updateParams()
    }

    fun prepareForTransition() {
        if (!canEnterPictureInPicture()) return
        if (!isVideoOnlyPresentation()) {
            startedFullscreen = isCurrentWindowFullscreen()
            sourceRectHint = currentSourceRect()
        }
        setVideoOnlyPresentation(true)
        browserController.prepareForPictureInPicture()
        updateParams()
    }

    fun cancelTransition() {
        returnInProgress = false
        startedFullscreen = false
        modeEntered = false
        cancelReturnLayoutWait()
        setVideoOnlyPresentation(false)
        sourceRectHint = null
        browserController.cancelPictureInPictureTransition()
        updateParams()
    }

    fun reconcileStateOnResume() {
        if (
            !activity.isInPictureInPictureMode &&
            !modeEntered &&
            !returnInProgress &&
            isVideoOnlyPresentation()
        ) {
            cancelTransition()
        }
    }

    fun updateParams() {
        if (!supportsPictureInPicture()) return
        val autoEnterEnabled = canEnterPictureInPicture()
        val nextSourceRectHint = eligibleSourceRect(autoEnterEnabled)
        val nextState = AppliedPictureInPictureState(autoEnterEnabled, nextSourceRectHint)
        if (appliedState == nextState) return
        appliedState = nextState
        activity.setPictureInPictureParams(
            buildParams(autoEnterEnabled, nextSourceRectHint),
        )
    }

    fun isEligible(): Boolean = canEnterPictureInPicture()

    fun appliedSourceRectHint(): Rect? = appliedState?.sourceRectHint?.let(::Rect)

    private fun completeReturnAfterLayout(configuration: Configuration) {
        cancelReturnLayoutWait()
        val decorView = activity.window.decorView
        val density = activity.resources.displayMetrics.density
        val targetWidth = (configuration.screenWidthDp * density).toInt()
        val targetHeight = (configuration.screenHeightDp * density).toInt()
        val tolerance = (RETURN_LAYOUT_TOLERANCE_DP * density).toInt()
        fun isExpandedLayout(width: Int, height: Int): Boolean =
            FullscreenVideoRules.isPictureInPictureReturnLayoutReady(
                width = width,
                height = height,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
                tolerance = tolerance,
            )
        if (isExpandedLayout(decorView.width, decorView.height)) {
            finishReturn()
            return
        }
        val listener = View.OnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            if (
                !activity.isInPictureInPictureMode &&
                isExpandedLayout(right - left, bottom - top)
            ) {
                cancelReturnLayoutWait()
                finishReturn()
            }
        }
        returnLayoutListener = listener
        decorView.addOnLayoutChangeListener(listener)
        decorView.postDelayed(
            {
                if (
                    returnLayoutListener === listener &&
                    !activity.isInPictureInPictureMode
                ) {
                    cancelReturnLayoutWait()
                    finishReturn()
                }
            },
            RETURN_LAYOUT_TIMEOUT_MILLIS,
        )
    }

    private fun cancelReturnLayoutWait() {
        val listener = returnLayoutListener ?: return
        activity.window.decorView.removeOnLayoutChangeListener(listener)
        returnLayoutListener = null
    }

    private fun finishReturn() {
        returnInProgress = false
        startedFullscreen = false
        modeEntered = false
        setVideoOnlyPresentation(false)
        sourceRectHint = null
        browserController.completePictureInPictureReturn()
        applyBrowserSystemUi()
        updateParams()
    }

    private fun buildParams(
        autoEnterEnabled: Boolean,
        sourceRectHint: Rect?,
    ): PictureInPictureParams = PictureInPictureParams.Builder()
        .setAspectRatio(Rational(VIDEO_ASPECT_WIDTH, VIDEO_ASPECT_HEIGHT))
        .setAutoEnterEnabled(autoEnterEnabled)
        .setSeamlessResizeEnabled(true)
        .setSourceRectHint(sourceRectHint)
        .build()

    private fun eligibleSourceRect(autoEnterEnabled: Boolean): Rect? =
        currentSourceRect()
            ?.takeIf { autoEnterEnabled && !it.isEmpty }
            ?.let(::Rect)

    private fun currentSourceRect(): Rect? {
        sourceRectHint?.let { return Rect(it) }
        val windowBounds = Rect()
        val visibleBounds = if (
            activity.window.decorView.getGlobalVisibleRect(windowBounds) && !windowBounds.isEmpty
        ) {
            windowBounds
        } else {
            fullscreenVideoBounds ?: return null
        }
        return pictureInPictureSourceRect(visibleBounds)
    }

    private fun pictureInPictureSourceRect(bounds: Rect): Rect? {
        val sourceBounds = FullscreenVideoRules.pictureInPictureSourceBounds(
            windowBounds = FullscreenVideoBounds(
                left = bounds.left,
                top = bounds.top,
                right = bounds.right,
                bottom = bounds.bottom,
            ),
            aspectWidth = VIDEO_ASPECT_WIDTH,
            aspectHeight = VIDEO_ASPECT_HEIGHT,
        ) ?: return null
        return Rect(
            sourceBounds.left,
            sourceBounds.top,
            sourceBounds.right,
            sourceBounds.bottom,
        )
    }

    private fun maximumWindowContentBounds(): Rect {
        val bounds = activity.windowManager.maximumWindowMetrics.bounds
        return Rect(0, 0, bounds.width(), bounds.height())
    }

    private fun isCurrentWindowFullscreen(): Boolean {
        val visibleBounds = Rect()
        if (
            !activity.window.decorView.getGlobalVisibleRect(visibleBounds) ||
            visibleBounds.isEmpty
        ) {
            return false
        }
        val maximumBounds = activity.windowManager.maximumWindowMetrics.bounds
        val tolerance = (
            RETURN_LAYOUT_TOLERANCE_DP * activity.resources.displayMetrics.density
            ).toInt()
        return FullscreenVideoRules.isPictureInPictureReturnLayoutReady(
            width = visibleBounds.width(),
            height = visibleBounds.height(),
            targetWidth = maximumBounds.width(),
            targetHeight = maximumBounds.height(),
            tolerance = tolerance,
        )
    }

    private fun canEnterPictureInPicture(): Boolean =
        supportsPictureInPicture() && browserController.isPictureInPictureEligible

    private fun supportsPictureInPicture(): Boolean = activity.packageManager.hasSystemFeature(
        PackageManager.FEATURE_PICTURE_IN_PICTURE,
    )

    private companion object {
        const val VIDEO_ASPECT_WIDTH = 16
        const val VIDEO_ASPECT_HEIGHT = 9
        const val RETURN_LAYOUT_TOLERANCE_DP = 8
        const val RETURN_LAYOUT_TIMEOUT_MILLIS = 3_000L
    }
}

private data class AppliedPictureInPictureState(
    val autoEnterEnabled: Boolean,
    val sourceRectHint: Rect?,
)
