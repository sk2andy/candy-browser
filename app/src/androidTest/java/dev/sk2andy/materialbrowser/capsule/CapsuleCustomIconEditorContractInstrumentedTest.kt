package dev.sk2andy.materialbrowser.capsule

import android.app.Activity
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.CapsuleCustomIconEditorActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CapsuleCustomIconEditorContractInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val contract = CapsuleCustomIconEditorContract()

    @Test
    fun boundedIconRoundTripsThroughExplicitEditorActivity() {
        val icon = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        val requestIntent = contract.createIntent(context, icon)
        val restored = requireNotNull(
            CapsuleCustomIconEditorContract.currentIconFrom(requestIntent),
        )
        val result = requireNotNull(
            contract.parseResult(
                Activity.RESULT_OK,
                CapsuleCustomIconEditorContract.resultIntent(icon),
            ),
        )

        assertEquals(CapsuleCustomIconEditorActivity::class.java.name, requestIntent.component?.className)
        assertEquals(192, restored.width)
        assertEquals(192, result.height)
        assertNull(
            contract.parseResult(
                Activity.RESULT_CANCELED,
                CapsuleCustomIconEditorContract.resultIntent(icon),
            ),
        )

        icon.recycle()
        restored.recycle()
        result.recycle()
    }
}
