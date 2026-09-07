package dev.sk2andy.materialbrowser.capsule

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.result.contract.ActivityResultContract
import dev.sk2andy.materialbrowser.CapsuleIconPackPickerActivity

class CapsuleIconPackPickerContract : ActivityResultContract<Unit, Bitmap?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(context, CapsuleIconPackPickerActivity::class.java)

    override fun parseResult(resultCode: Int, intent: Intent?): Bitmap? {
        if (resultCode != Activity.RESULT_OK || intent == null) return null
        return CapsuleCustomIconEditorContract.decodeIcon(
            intent.getByteArrayExtra(EXTRA_RESULT_ICON),
        )
    }

    companion object {
        private const val EXTRA_RESULT_ICON = "capsule_icon_pack.result"

        fun resultIntent(icon: Bitmap): Intent? =
            CapsuleCustomIconEditorContract.encodeIcon(icon)?.let { bytes ->
                Intent().putExtra(EXTRA_RESULT_ICON, bytes)
            }
    }
}
