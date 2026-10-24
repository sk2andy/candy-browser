package dev.sk2andy.materialbrowser.browser.sitewidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.SiteWidgetProvider
import dev.sk2andy.materialbrowser.data.SiteWidgetGridSizingStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class SiteWidgetProviderInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun currentLauncherIsVisibleForLearningWidgetDimensions() {
        assertNotNull(SiteWidgetGridSizingStore(context).currentLauncherPackage())
    }

    @Test
    fun installedProvidersExposeEverySelectableGridSize() {
        val providers = AppWidgetManager.getInstance(context).installedProviders
        SiteWidgetFormat.entries.forEach { format ->
            val component = SiteWidgetProvider.component(context, format)
            val provider = providers.firstOrNull { it.provider == component }
            assertNotNull("Site widget provider must be installed: $component", provider)
            requireNotNull(provider)
            assertEquals(format.columns, provider.targetCellWidth)
            assertEquals(format.rows, provider.targetCellHeight)
            assertEquals(AppWidgetProviderInfo.RESIZE_NONE, provider.resizeMode)
            assertEquals(0, provider.updatePeriodMillis)
        }
    }

    @Test
    fun snapshotViewsRenderImageAndBlankReapplyClearsPreviousSourceAndActions() {
        val source = SiteWidgetSource(
            tabId = "widget-tab",
            profileId = "default",
            url = "https://widget.example/status?token=private-value",
            title = "My server status",
            navigationGeneration = 4,
        )
        val snapshot = SiteWidgetSnapshot(
            id = "bf7f3b28-4ce5-4f97-9352-389714cd668c",
            source = source,
            format = SiteWidgetFormat.FourByThree,
            crop = SiteWidgetCrop(left = 0f, top = 0f, width = 1f, height = 1f),
            createdAtMillis = 1_700_000_000_000L,
        )
        val bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(0, 127, 153))
        }
        try {
            instrumentation.runOnMainSync {
                val views = SiteWidgetProvider.views(context, snapshot, bitmap, widgetId = 4_231)
                val root = views.apply(context, FrameLayout(context))
                val image = root.findViewById<ImageView>(R.id.site_widget_image)
                val empty = root.findViewById<TextView>(R.id.site_widget_empty)
                val refresh = root.findViewById<View>(R.id.site_widget_refresh)

                assertEquals(View.VISIBLE, image.visibility)
                assertEquals(View.GONE, empty.visibility)
                assertEquals(View.VISIBLE, refresh.visibility)
                assertEquals(source.title, image.contentDescription.toString())
                assertEquals(ImageView.ScaleType.CENTER_CROP, image.scaleType)
                assertEquals(0, root.paddingTop)
                assertEquals(0, root.paddingBottom)
                assertTrue(root.clipToOutline)
                val margin = refresh.layoutParams as FrameLayout.LayoutParams
                assertTrue(margin.topMargin > 0)
                assertTrue(margin.marginEnd > 0)
                assertEquals(margin.width, margin.height)
                assertEquals(GradientDrawable.OVAL, (refresh.background as GradientDrawable).shape)
                val rendered = (image.drawable as BitmapDrawable).bitmap
                assertEquals(32, rendered.width)
                assertEquals(24, rendered.height)
                assertEquals(Color.rgb(0, 127, 153), rendered.getPixel(16, 12))
                assertTrue(image.hasOnClickListeners())
                assertTrue(refresh.hasOnClickListeners())

                SiteWidgetProvider.views(context, null, null, widgetId = 4_231).reapply(context, root)

                assertEquals(View.GONE, image.visibility)
                assertFalse("Cleared widget must release its source bitmap", image.drawable is BitmapDrawable)
                assertEquals(View.VISIBLE, empty.visibility)
                assertEquals(View.GONE, refresh.visibility)
                assertEquals(context.getString(R.string.site_widget_open), image.contentDescription.toString())
                assertFalse(image.hasOnClickListeners())
                assertFalse(refresh.hasOnClickListeners())
                assertTrue("An empty bound widget must retain its setup action", empty.hasOnClickListeners())
                assertNoSourceMetadata(root, source)

                SiteWidgetProvider.views(context, null, null).reapply(context, root)

                listOf(image, empty, refresh).forEach { view ->
                    assertFalse("Unbound empty widgets must clear previous actions", view.hasOnClickListeners())
                }
                assertNoSourceMetadata(root, source)
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun launcherAspectRatiosFillEveryPixelWithoutDistortingImage() {
        val snapshot = SiteWidgetSnapshot(
            id = "bf7f3b28-4ce5-4f97-9352-389714cd668c",
            source = SiteWidgetSource(
                tabId = "widget-tab",
                profileId = "default",
                url = "https://widget.example/status",
                title = "My server status",
                navigationGeneration = 4,
            ),
            format = SiteWidgetFormat.FourByFour,
            crop = SiteWidgetCrop(left = 0f, top = 0f, width = 1f, height = 1f),
            createdAtMillis = 1_700_000_000_000L,
        )
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.GREEN)
            for (index in 0 until 100) {
                setPixel(0, index, Color.RED)
                setPixel(99, index, Color.BLUE)
                setPixel(index, 0, Color.YELLOW)
                setPixel(index, 99, Color.MAGENTA)
            }
        }
        try {
            instrumentation.runOnMainSync {
                val root = SiteWidgetProvider.views(context, snapshot, bitmap).apply(context, FrameLayout(context))
                val image = root.findViewById<ImageView>(R.id.site_widget_image)
                listOf(200 to 300, 300 to 200, 200 to 200).forEach { (width, height) ->
                    root.measure(
                        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
                    )
                    root.layout(0, 0, width, height)
                    val rendered = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    try {
                        image.draw(Canvas(rendered))
                        for (x in 0 until width) {
                            assertEquals(255, Color.alpha(rendered.getPixel(x, 0)))
                            assertEquals(255, Color.alpha(rendered.getPixel(x, height - 1)))
                        }
                        for (y in 0 until height) {
                            assertEquals(255, Color.alpha(rendered.getPixel(0, y)))
                            assertEquals(255, Color.alpha(rendered.getPixel(width - 1, y)))
                        }
                        assertEquals(Color.GREEN, rendered.getPixel(width / 2, height / 2))
                        val matrixValues = FloatArray(9)
                        image.imageMatrix.getValues(matrixValues)
                        assertEquals(matrixValues[0], matrixValues[4], 0.0001f)
                    } finally {
                        rendered.recycle()
                    }
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun assertNoSourceMetadata(view: View, source: SiteWidgetSource) {
        val renderedText = (view as? TextView)?.text?.toString().orEmpty()
        val description = view.contentDescription?.toString().orEmpty()
        listOf(source.url, source.title, "widget.example").forEach { sensitiveText ->
            assertFalse("Empty widget retained source text", renderedText.contains(sensitiveText))
            assertFalse("Empty widget retained source accessibility text", description.contains(sensitiveText))
        }
        if (view is ViewGroup) {
            repeat(view.childCount) { index -> assertNoSourceMetadata(view.getChildAt(index), source) }
        }
    }
}
