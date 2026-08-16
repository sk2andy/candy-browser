package dev.sk2andy.materialbrowser

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

internal class FullscreenWebContentHost(
    private val activity: ComponentActivity,
    private val restoreBrowserWindowState: () -> Unit,
) {
    private var container: FrameLayout? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var backCallback: OnBackPressedCallback? = null

    val isShowing: Boolean
        get() = container != null

    fun show(
        view: View,
        callback: WebChromeClient.CustomViewCallback,
    ): Boolean {
        if (isShowing) {
            callback.onCustomViewHidden()
            return false
        }

        customViewCallback = callback
        val fullscreenContainer = FrameLayout(activity).apply {
            setBackgroundColor(Color.BLACK)
            (view.parent as? ViewGroup)?.removeView(view)
            addView(
                view,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        container = fullscreenContainer
        (activity.window.decorView as ViewGroup).addView(
            fullscreenContainer,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        activity.applyFullImmersiveMode(true)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                dismiss(notifyWebContent = true)
            }
        }.also(activity.onBackPressedDispatcher::addCallback)
        return true
    }

    fun hideFromWebContent() {
        dismiss(notifyWebContent = false)
    }

    fun dismissFromBrowser() {
        dismiss(notifyWebContent = true)
    }

    private fun dismiss(notifyWebContent: Boolean) {
        val fullscreenContainer = container ?: return
        val callback = customViewCallback
        container = null
        customViewCallback = null
        backCallback?.remove()
        backCallback = null

        fullscreenContainer.removeAllViews()
        (fullscreenContainer.parent as? ViewGroup)?.removeView(fullscreenContainer)
        restoreBrowserWindowState()

        if (notifyWebContent) callback?.onCustomViewHidden()
    }
}
