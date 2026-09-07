package dev.sk2andy.materialbrowser.capsule

import android.app.Activity
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.CapsuleIconPackPickerActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CapsuleIconPackPickerContractInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val contract = CapsuleIconPackPickerContract()

    @Test
    fun boundedIconRoundTripsThroughExplicitPickerActivity() {
        val icon = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        val request = contract.createIntent(context, Unit)
        val resultIntent = requireNotNull(CapsuleIconPackPickerContract.resultIntent(icon))
        val result = requireNotNull(contract.parseResult(Activity.RESULT_OK, resultIntent))

        assertEquals(CapsuleIconPackPickerActivity::class.java.name, request.component?.className)
        assertEquals(192, result.width)
        assertEquals(192, result.height)
        assertNull(contract.parseResult(Activity.RESULT_CANCELED, resultIntent))

        icon.recycle()
        result.recycle()
    }
}
