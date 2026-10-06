package dev.sk2andy.materialbrowser.browser.gecko

import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import dev.sk2andy.materialbrowser.BuildConfig
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class GeckoViewRebindInsetsInstrumentedTest {
    @Test
    fun nativeMarginsAreSeededBeforeReattachmentAndCurrentLayoutCanReplaceThem() {
        assumeFalse(BuildConfig.SYSTEM_WEBVIEW_ONLY)
        ActivityScenario.launch(GeckoScrollTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                val session = GeckoRuntimeOwner.getOrCreate(activity).createSession(
                    profileId = "rebind-insets-${UUID.randomUUID()}",
                    isPrivate = false,
                    privacyPolicy = GeckoPrivacyPolicy.Disabled,
                )
                var view = session.createView(activity) as CandyGeckoView
                try {
                    assertLayout(view, GeckoViewInsets.Zero, rendererInsets = null)
                    val portrait = GeckoViewInsets(left = 24, top = 144, right = 36, bottom = 80)
                    updateLayout(view, portrait, forceNativeSafeArea = true)
                    activity.setContentView(view)
                    val previous = view
                    session.releaseView(previous)
                    view = session.createView(activity) as CandyGeckoView

                    assertNotSame(previous, view)
                    // No parent, root WindowInsets or Controller callback exists at this point.
                    assertEquals(null, view.parent)
                    assertLayout(view, portrait, rendererInsets = GeckoViewInsets.Zero)

                    val landscape = GeckoViewInsets(left = 100, top = 0, right = 60, bottom = 40)
                    updateLayout(view, landscape, forceNativeSafeArea = true)
                    assertLayout(view, landscape, rendererInsets = GeckoViewInsets.Zero)
                    activity.setContentView(view)

                    updateLayout(view, landscape, isFullscreenContent = true)
                    assertLayout(view, GeckoViewInsets.Zero, rendererInsets = null)
                    session.releaseView(view)
                    view = session.createView(activity) as CandyGeckoView
                    assertLayout(view, GeckoViewInsets.Zero, rendererInsets = null)
                } finally {
                    session.releaseView(view)
                    session.close()
                }
            }
        }
    }

    @Test
    fun rendererCssOwnershipSurvivesRebindWithoutRetainingWindowInsets() {
        assumeFalse(BuildConfig.SYSTEM_WEBVIEW_ONLY)
        ActivityScenario.launch(GeckoScrollTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val session = GeckoRuntimeOwner.getOrCreate(activity).createSession(
                    profileId = "rebind-css-insets-${UUID.randomUUID()}",
                    isPrivate = false,
                    privacyPolicy = GeckoPrivacyPolicy.Disabled,
                )
                var view = session.createView(activity) as CandyGeckoView
                try {
                    val insets = GeckoViewInsets(left = 12, top = 144, right = 24, bottom = 80)
                    updateLayout(view, insets)
                    activity.setContentView(view)
                    session.releaseView(view)
                    view = session.createView(activity) as CandyGeckoView
                    assertLayout(view, GeckoViewInsets.Zero, rendererInsets = insets)

                    val safeDrawingLayout = GeckoViewInsetRules.resolve(
                        safeArea = insets,
                        forceNativeSafeArea = false,
                        forceNativeTopSafeArea = false,
                        isFullscreenContent = false,
                        isInsideSafeDrawingHost = true,
                    )
                    view.updateInsets(safeDrawingLayout, WindowInsetsCompat.Builder().build())
                    assertLayout(view, GeckoViewInsets.Zero, rendererInsets = GeckoViewInsets.Zero)
                } finally {
                    session.releaseView(view)
                    session.close()
                }
            }
        }
    }

    private fun updateLayout(
        view: CandyGeckoView,
        safeArea: GeckoViewInsets,
        forceNativeSafeArea: Boolean = false,
        isFullscreenContent: Boolean = false,
    ) {
        view.updateInsets(
            GeckoViewInsetRules.resolve(
                safeArea = safeArea,
                forceNativeSafeArea = forceNativeSafeArea,
                forceNativeTopSafeArea = false,
                isFullscreenContent = isFullscreenContent,
                isInsideSafeDrawingHost = false,
            ),
            WindowInsetsCompat.Builder()
                .setInsets(
                    WindowInsetsCompat.Type.systemBars(),
                    Insets.of(safeArea.left, safeArea.top, safeArea.right, safeArea.bottom),
                )
                .build(),
        )
    }

    private fun assertLayout(
        view: CandyGeckoView,
        margins: GeckoViewInsets,
        rendererInsets: GeckoViewInsets?,
    ) {
        val engine = view.getChildAt(0)
        val actualMargins = engine.layoutParams as ViewGroup.MarginLayoutParams
        assertEquals(margins.left, actualMargins.leftMargin)
        assertEquals(margins.top, actualMargins.topMargin)
        assertEquals(margins.right, actualMargins.rightMargin)
        assertEquals(margins.bottom, actualMargins.bottomMargin)
        val native = view.domDiagnosticInsets()
        if (rendererInsets == null) {
            assertEquals(true, native.isNull("topPx"))
        } else {
            assertEquals(rendererInsets.top, native.getInt("topPx"))
            assertEquals(rendererInsets.left, native.getInt("leftPx"))
            assertEquals(rendererInsets.right, native.getInt("rightPx"))
            assertEquals(rendererInsets.bottom, native.getInt("bottomPx"))
        }
    }
}
