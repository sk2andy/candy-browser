package dev.sk2andy.materialbrowser.capsule

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.result.contract.ActivityResultContract
import dev.sk2andy.materialbrowser.CapsuleCustomIconEditorActivity
import java.io.ByteArrayOutputStream

data class CapsuleCustomIconEditorRequest(
    val icon: Bitmap?,
    val protectedProfileIds: Set<String>,
)

class CapsuleCustomIconEditorContract :
    ActivityResultContract<CapsuleCustomIconEditorRequest, Bitmap?>() {
    override fun createIntent(context: Context, input: CapsuleCustomIconEditorRequest): Intent =
        Intent(context, CapsuleCustomIconEditorActivity::class.java).apply {
            input.icon?.let(::encodeIcon)?.let { putExtra(EXTRA_CURRENT_ICON, it) }
            putStringArrayListExtra(
                EXTRA_PROTECTED_PROFILE_IDS,
                ArrayList(input.protectedProfileIds),
            )
        }

    override fun parseResult(resultCode: Int, intent: Intent?): Bitmap? {
        if (resultCode != Activity.RESULT_OK || intent == null) return null
        return decodeIcon(intent.getByteArrayExtra(EXTRA_RESULT_ICON))
    }

    companion object {
        private const val MAX_BYTES = 256 * 1_024
        private const val EXTRA_CURRENT_ICON = "capsule_custom_icon.current"
        private const val EXTRA_PROTECTED_PROFILE_IDS =
            "capsule_custom_icon.protected_profile_ids"
        private const val EXTRA_RESULT_ICON = "capsule_custom_icon.result"

        fun currentIconFrom(intent: Intent): Bitmap? =
            decodeIcon(intent.getByteArrayExtra(EXTRA_CURRENT_ICON))

        fun protectedProfileIdsFrom(intent: Intent): Set<String> =
            intent.getStringArrayListExtra(EXTRA_PROTECTED_PROFILE_IDS)
                .orEmpty()
                .filterTo(linkedSetOf()) { profileId ->
                    profileId.isNotBlank() &&
                        profileId.length <= SiteCapsuleRules.MAX_PROFILE_ID_LENGTH &&
                        profileId.none(Char::isISOControl)
                }

        fun resultIntent(icon: Bitmap): Intent = Intent().apply {
            encodeIcon(icon)?.let { putExtra(EXTRA_RESULT_ICON, it) }
        }

        internal fun decodeIcon(bytes: ByteArray?): Bitmap? {
            val bounded = bytes?.takeIf { it.size in 1..MAX_BYTES } ?: return null
            val bitmap = BitmapFactory.decodeByteArray(bounded, 0, bounded.size) ?: return null
            return if (
                bitmap.width in 1..CapsuleCustomIconProcessor.OUTPUT_SIZE &&
                bitmap.height in 1..CapsuleCustomIconProcessor.OUTPUT_SIZE &&
                bitmap.width == bitmap.height
            ) {
                bitmap
            } else {
                bitmap.recycle()
                null
            }
        }

        internal fun encodeIcon(icon: Bitmap): ByteArray? = with(icon) {
            if (
                isRecycled ||
                width !in 1..CapsuleCustomIconProcessor.OUTPUT_SIZE ||
                height !in 1..CapsuleCustomIconProcessor.OUTPUT_SIZE ||
                width != height
            ) return null
            return ByteArrayOutputStream().use { output ->
                if (compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    output.toByteArray().takeIf { it.size <= MAX_BYTES }
                } else {
                    null
                }
            }
        }
    }
}
