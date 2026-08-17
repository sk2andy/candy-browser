package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsIconInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun toppingsIconDrawsVisiblePixels() {
        val drawable = requireNotNull(context.getDrawable(R.drawable.ic_symbol_extension)).mutate()
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        drawable.setTint(Color.BLACK)
        drawable.draw(Canvas(bitmap))

        val visiblePixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(visiblePixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        assertTrue(visiblePixels.count { Color.alpha(it) > 0 } > 1_000)
    }
}
