package dev.sk2andy.materialbrowser.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import dev.sk2andy.materialbrowser.R
import java.util.WeakHashMap

internal class CandyMotionDurationScale(
    animationsEnabled: Boolean,
) : MotionDurationScale {
    override var scaleFactor by mutableFloatStateOf(scaleFactor(animationsEnabled))
        private set

    fun updateAnimationsEnabled(animationsEnabled: Boolean) {
        scaleFactor = scaleFactor(animationsEnabled)
    }

    private companion object {
        const val ENABLED_SCALE_FACTOR = 1f
        const val DISABLED_SCALE_FACTOR = 0f

        fun scaleFactor(animationsEnabled: Boolean): Float =
            if (animationsEnabled) ENABLED_SCALE_FACTOR else DISABLED_SCALE_FACTOR
    }
}

internal fun ComponentActivity.setCandyContent(
    animationsEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    val composeView = ComposeView(this)
    CandyActivityMotionPolicy.apply(this, animationsEnabled)
    setContentView(
        composeView,
        ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ),
    )
    val recomposer = composeView.createLifecycleAwareWindowRecomposer(
        coroutineContext = CandyMotionDurationScale(animationsEnabled),
    )
    composeView.setParentCompositionContext(recomposer)
    composeView.setContent(content)
}

internal fun Context.findCandyActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext
        .takeUnless { context -> context === this }
        ?.findCandyActivity()
    else -> null
}

internal object CandyActivityMotionPolicy {
    private val originalWindowAnimations = WeakHashMap<Activity, Int>()

    @Suppress("DEPRECATION")
    fun apply(activity: Activity, animationsEnabled: Boolean) {
        val originalWindowAnimation = synchronized(originalWindowAnimations) {
            originalWindowAnimations.getOrPut(activity) {
                activity.window.attributes.windowAnimations
            }
        }
        activity.window.setWindowAnimations(
            if (animationsEnabled) {
                originalWindowAnimation
            } else {
                R.style.CandyWindowAnimationNone
            },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (animationsEnabled) {
                activity.clearOverrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN)
                activity.clearOverrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE)
            } else {
                activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
                activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
            }
        } else if (!animationsEnabled) {
            activity.overridePendingTransition(0, 0)
        }
    }
}
