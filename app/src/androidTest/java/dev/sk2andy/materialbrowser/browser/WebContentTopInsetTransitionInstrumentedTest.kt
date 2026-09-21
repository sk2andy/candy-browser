package dev.sk2andy.materialbrowser.browser

import android.animation.ValueAnimator
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebContentTopInsetTransitionInstrumentedTest {
    @Test
    fun nativeHeaderTransitionKeepsTopEdgeAndSettlesAtFinalLayout() {
        assumeTrue(ValueAnimator.areAnimatorsEnabled())
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var content: View
            scenario.onActivity { activity ->
                val root = FrameLayout(activity)
                content = View(activity)
                root.addView(
                    content,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
                activity.setContentView(root)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity {
                val layoutParams = content.layoutParams as FrameLayout.LayoutParams
                layoutParams.topMargin = 96
                content.layoutParams = layoutParams
                content.smoothWebContentTopInsetChange(
                    previousTopInsetPx = 0,
                    nextTopInsetPx = 96,
                    animateChange = true,
                )

                assertEquals(-96f, content.translationY, 0.5f)
            }

            SystemClock.sleep(WebContentTopInsetTransitionRules.DURATION_MILLIS + 100L)
            scenario.onActivity {
                assertEquals(0f, content.translationY, 0.5f)
                assertEquals(96, (content.layoutParams as FrameLayout.LayoutParams).topMargin)
            }
        }
    }

    @Test
    fun nonHeaderInsetChangeCancelsRunningTransitionAndSnaps() {
        assumeTrue(ValueAnimator.areAnimatorsEnabled())
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var content: View
            scenario.onActivity { activity ->
                val root = FrameLayout(activity)
                content = View(activity)
                root.addView(
                    content,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
                activity.setContentView(root)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity {
                val layoutParams = content.layoutParams as FrameLayout.LayoutParams
                layoutParams.topMargin = 96
                content.layoutParams = layoutParams
                content.smoothWebContentTopInsetChange(
                    previousTopInsetPx = 0,
                    nextTopInsetPx = 96,
                    animateChange = true,
                )

                layoutParams.topMargin = 0
                content.layoutParams = layoutParams
                content.smoothWebContentTopInsetChange(
                    previousTopInsetPx = 96,
                    nextTopInsetPx = 0,
                    animateChange = false,
                )

                assertEquals(0f, content.translationY, 0f)
                assertEquals(0, layoutParams.topMargin)
            }
        }
    }
}
