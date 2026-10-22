package dev.sk2andy.materialbrowser

import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RssFeedWidgetLayoutInstrumentedTest {
    @Test
    fun frameworkRemoteViewsCanInflateTheFeedWidget() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val widget = RemoteViews(context.packageName, R.layout.rss_feed_widget)
                .apply(context, FrameLayout(context))
            assertNotNull(widget.findViewById<TextView>(R.id.rss_widget_title))
            assertNotNull(widget.findViewById<TextView>(R.id.rss_widget_divider_one))
            assertNotNull(widget.findViewById<TextView>(R.id.rss_widget_divider_two))
        }
    }
}
