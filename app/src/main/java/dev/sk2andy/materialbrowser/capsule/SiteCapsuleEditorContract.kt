package dev.sk2andy.materialbrowser.capsule

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.result.contract.ActivityResultContract
import dev.sk2andy.materialbrowser.SiteCapsuleEditorActivity
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy
import java.io.ByteArrayOutputStream

data class SiteCapsuleEditorRequest(
    val existing: SiteCapsule?,
    val sourceTabId: String?,
    val sourceTitle: String,
    val sourceUrl: String,
    val profiles: List<BrowserProfile>,
    val activeProfileId: String,
    val profileIsolationSupported: Boolean,
    val pinningSupported: Boolean,
    val canCreate: Boolean,
    val canCreateDedicatedProfile: Boolean,
    val previewIcon: Bitmap?,
)

data class SiteCapsuleEditorSubmission(
    val existingId: String?,
    val sourceTabId: String?,
    val name: String,
    val startUrl: String,
    val selectedProfileId: String,
    val createDedicatedProfile: Boolean,
    val dedicatedEmoji: String,
    val isolatedStorageRequested: Boolean,
    val navigationMode: CapsuleNavigationMode,
    val chromeMode: CapsuleChromeMode,
    val iconMode: CapsuleIconMode,
    val sourceFavicon: Bitmap?,
)

class SiteCapsuleEditorContract :
    ActivityResultContract<SiteCapsuleEditorRequest, SiteCapsuleEditorSubmission?>() {
    override fun createIntent(context: Context, input: SiteCapsuleEditorRequest): Intent =
        Intent(context, SiteCapsuleEditorActivity::class.java).apply {
            putExtra(EXTRA_EXISTING_ID, input.existing?.id)
            putExtra(EXTRA_SOURCE_TAB_ID, input.sourceTabId)
            putExtra(EXTRA_SOURCE_TITLE, input.sourceTitle.take(SiteCapsuleRules.MAX_NAME_LENGTH))
            putExtra(EXTRA_SOURCE_URL, input.sourceUrl.take(SiteCapsuleRules.MAX_URL_LENGTH))
            putStringArrayListExtra(
                EXTRA_PROFILE_IDS,
                ArrayList(input.profiles.map(BrowserProfile::id)),
            )
            putStringArrayListExtra(
                EXTRA_PROFILE_EMOJIS,
                ArrayList(input.profiles.map(BrowserProfile::emoji)),
            )
            putExtra(EXTRA_ACTIVE_PROFILE_ID, input.activeProfileId)
            putExtra(EXTRA_PROFILE_ISOLATION_SUPPORTED, input.profileIsolationSupported)
            putExtra(EXTRA_PINNING_SUPPORTED, input.pinningSupported)
            putExtra(EXTRA_CAN_CREATE, input.canCreate)
            putExtra(EXTRA_CAN_CREATE_DEDICATED_PROFILE, input.canCreateDedicatedProfile)
            input.previewIcon?.toPreviewPng()?.let { putExtra(EXTRA_PREVIEW_ICON, it) }
            input.existing?.let { existing ->
                putExtra(EXTRA_EXISTING_NAME, existing.name)
                putExtra(EXTRA_EXISTING_URL, existing.startUrl)
                putExtra(EXTRA_EXISTING_PROFILE_ID, existing.profileId)
                putExtra(EXTRA_EXISTING_OWNS_PROFILE, existing.ownsDedicatedProfile)
                putExtra(EXTRA_EXISTING_ISOLATED, existing.isolatedStorageRequested)
                putExtra(EXTRA_EXISTING_NAVIGATION, existing.navigationMode.wireValue)
                putExtra(EXTRA_EXISTING_CHROME, existing.chromeMode.wireValue)
                putExtra(EXTRA_EXISTING_ICON, existing.iconMode.wireValue)
                putExtra(EXTRA_EXISTING_CREATED_AT, existing.createdAtMillis)
                putExtra(EXTRA_EXISTING_UPDATED_AT, existing.updatedAtMillis)
            }
        }

    override fun parseResult(resultCode: Int, intent: Intent?): SiteCapsuleEditorSubmission? {
        if (resultCode != Activity.RESULT_OK || intent == null) return null
        val name = intent.safeString(EXTRA_RESULT_NAME, SiteCapsuleRules.MAX_NAME_LENGTH)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return null
        val url = intent.safeString(EXTRA_RESULT_URL, SiteCapsuleRules.MAX_URL_LENGTH)
            ?.takeIf { BrowserUriPolicy.normalizeHttpUrl(it) != null }
            ?: return null
        val profileId = intent.safeString(
            EXTRA_RESULT_PROFILE_ID,
            SiteCapsuleRules.MAX_PROFILE_ID_LENGTH,
        )?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return SiteCapsuleEditorSubmission(
            existingId = intent.safeString(EXTRA_RESULT_EXISTING_ID, MAX_OPAQUE_ID_LENGTH),
            sourceTabId = intent.safeString(EXTRA_RESULT_SOURCE_TAB_ID, MAX_OPAQUE_ID_LENGTH),
            name = name,
            startUrl = url,
            selectedProfileId = profileId,
            createDedicatedProfile = intent.getBooleanExtra(
                EXTRA_RESULT_CREATE_DEDICATED,
                false,
            ),
            dedicatedEmoji = intent.safeString(EXTRA_RESULT_DEDICATED_EMOJI, MAX_EMOJI_LENGTH)
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: DEFAULT_DEDICATED_EMOJI,
            isolatedStorageRequested = intent.getBooleanExtra(EXTRA_RESULT_ISOLATED, false),
            navigationMode = CapsuleNavigationMode.fromWireValue(
                intent.getStringExtra(EXTRA_RESULT_NAVIGATION),
            ),
            chromeMode = CapsuleChromeMode.fromWireValue(
                intent.getStringExtra(EXTRA_RESULT_CHROME),
            ),
            iconMode = CapsuleIconMode.fromWireValue(
                intent.getStringExtra(EXTRA_RESULT_ICON),
            ),
            sourceFavicon = decodePreviewPng(
                intent.getByteArrayExtra(EXTRA_RESULT_SOURCE_FAVICON),
            ),
        )
    }

    companion object {
        const val DEFAULT_DEDICATED_EMOJI = "🧩"
        private const val MAX_OPAQUE_ID_LENGTH = 128
        private const val MAX_EMOJI_LENGTH = 16
        private const val MAX_PREVIEW_DIMENSION = 96
        private const val MAX_PREVIEW_BYTES = 256 * 1_024

        private const val EXTRA_EXISTING_ID = "capsule_editor.existing_id"
        private const val EXTRA_SOURCE_TAB_ID = "capsule_editor.source_tab_id"
        private const val EXTRA_SOURCE_TITLE = "capsule_editor.source_title"
        private const val EXTRA_SOURCE_URL = "capsule_editor.source_url"
        private const val EXTRA_PROFILE_IDS = "capsule_editor.profile_ids"
        private const val EXTRA_PROFILE_EMOJIS = "capsule_editor.profile_emojis"
        private const val EXTRA_ACTIVE_PROFILE_ID = "capsule_editor.active_profile_id"
        private const val EXTRA_PROFILE_ISOLATION_SUPPORTED =
            "capsule_editor.profile_isolation_supported"
        private const val EXTRA_PINNING_SUPPORTED = "capsule_editor.pinning_supported"
        private const val EXTRA_CAN_CREATE = "capsule_editor.can_create"
        private const val EXTRA_CAN_CREATE_DEDICATED_PROFILE =
            "capsule_editor.can_create_dedicated_profile"
        private const val EXTRA_PREVIEW_ICON = "capsule_editor.preview_icon"
        private const val EXTRA_EXISTING_NAME = "capsule_editor.existing_name"
        private const val EXTRA_EXISTING_URL = "capsule_editor.existing_url"
        private const val EXTRA_EXISTING_PROFILE_ID = "capsule_editor.existing_profile_id"
        private const val EXTRA_EXISTING_OWNS_PROFILE = "capsule_editor.existing_owns_profile"
        private const val EXTRA_EXISTING_ISOLATED = "capsule_editor.existing_isolated"
        private const val EXTRA_EXISTING_NAVIGATION = "capsule_editor.existing_navigation"
        private const val EXTRA_EXISTING_CHROME = "capsule_editor.existing_chrome"
        private const val EXTRA_EXISTING_ICON = "capsule_editor.existing_icon"
        private const val EXTRA_EXISTING_CREATED_AT = "capsule_editor.existing_created_at"
        private const val EXTRA_EXISTING_UPDATED_AT = "capsule_editor.existing_updated_at"

        private const val EXTRA_RESULT_EXISTING_ID = "capsule_editor.result.existing_id"
        private const val EXTRA_RESULT_SOURCE_TAB_ID = "capsule_editor.result.source_tab_id"
        private const val EXTRA_RESULT_NAME = "capsule_editor.result.name"
        private const val EXTRA_RESULT_URL = "capsule_editor.result.url"
        private const val EXTRA_RESULT_PROFILE_ID = "capsule_editor.result.profile_id"
        private const val EXTRA_RESULT_CREATE_DEDICATED =
            "capsule_editor.result.create_dedicated"
        private const val EXTRA_RESULT_DEDICATED_EMOJI =
            "capsule_editor.result.dedicated_emoji"
        private const val EXTRA_RESULT_ISOLATED = "capsule_editor.result.isolated"
        private const val EXTRA_RESULT_NAVIGATION = "capsule_editor.result.navigation"
        private const val EXTRA_RESULT_CHROME = "capsule_editor.result.chrome"
        private const val EXTRA_RESULT_ICON = "capsule_editor.result.icon"
        private const val EXTRA_RESULT_SOURCE_FAVICON = "capsule_editor.result.source_favicon"

        fun requestFrom(intent: Intent): SiteCapsuleEditorRequest? {
            val profileIds = intent.getStringArrayListExtra(EXTRA_PROFILE_IDS).orEmpty()
            val profileEmojis = intent.getStringArrayListExtra(EXTRA_PROFILE_EMOJIS).orEmpty()
            val profiles = profileIds.zip(profileEmojis)
                .mapNotNull { (id, emoji) ->
                    val safeId = id.trim().takeIf {
                        it.isNotEmpty() && it.length <= SiteCapsuleRules.MAX_PROFILE_ID_LENGTH
                    } ?: return@mapNotNull null
                    val safeEmoji = emoji.trim().takeIf(String::isNotEmpty)
                        ?.take(MAX_EMOJI_LENGTH)
                        ?: return@mapNotNull null
                    BrowserProfile(safeId, safeEmoji)
                }
                .distinctBy(BrowserProfile::id)
            if (profiles.isEmpty()) return null
            val activeProfileId = intent.safeString(
                EXTRA_ACTIVE_PROFILE_ID,
                SiteCapsuleRules.MAX_PROFILE_ID_LENGTH,
            )?.takeIf { activeId -> profiles.any { it.id == activeId } } ?: profiles.first().id
            val existingId = intent.safeString(EXTRA_EXISTING_ID, MAX_OPAQUE_ID_LENGTH)
            val existing = existingId?.let { id ->
                SiteCapsule(
                    id = SiteCapsuleRules.opaqueId(id) ?: return null,
                    name = intent.safeString(
                        EXTRA_EXISTING_NAME,
                        SiteCapsuleRules.MAX_NAME_LENGTH,
                    ) ?: return null,
                    startUrl = intent.safeString(
                        EXTRA_EXISTING_URL,
                        SiteCapsuleRules.MAX_URL_LENGTH,
                    ) ?: return null,
                    profileId = intent.safeString(
                        EXTRA_EXISTING_PROFILE_ID,
                        SiteCapsuleRules.MAX_PROFILE_ID_LENGTH,
                    )?.takeIf { profileId -> profiles.any { it.id == profileId } } ?: return null,
                    ownsDedicatedProfile = intent.getBooleanExtra(
                        EXTRA_EXISTING_OWNS_PROFILE,
                        false,
                    ),
                    isolatedStorageRequested = intent.getBooleanExtra(
                        EXTRA_EXISTING_ISOLATED,
                        false,
                    ),
                    navigationMode = CapsuleNavigationMode.fromWireValue(
                        intent.getStringExtra(EXTRA_EXISTING_NAVIGATION),
                    ),
                    chromeMode = CapsuleChromeMode.fromWireValue(
                        intent.getStringExtra(EXTRA_EXISTING_CHROME),
                    ),
                    iconMode = CapsuleIconMode.fromWireValue(
                        intent.getStringExtra(EXTRA_EXISTING_ICON),
                    ),
                    createdAtMillis = intent.getLongExtra(EXTRA_EXISTING_CREATED_AT, 0L),
                    updatedAtMillis = intent.getLongExtra(EXTRA_EXISTING_UPDATED_AT, 0L),
                )
            }
            return SiteCapsuleEditorRequest(
                existing = existing,
                sourceTabId = intent.safeString(EXTRA_SOURCE_TAB_ID, MAX_OPAQUE_ID_LENGTH),
                sourceTitle = intent.safeString(
                    EXTRA_SOURCE_TITLE,
                    SiteCapsuleRules.MAX_NAME_LENGTH,
                ).orEmpty(),
                sourceUrl = intent.safeString(
                    EXTRA_SOURCE_URL,
                    SiteCapsuleRules.MAX_URL_LENGTH,
                ).orEmpty(),
                profiles = profiles,
                activeProfileId = activeProfileId,
                profileIsolationSupported = intent.getBooleanExtra(
                    EXTRA_PROFILE_ISOLATION_SUPPORTED,
                    false,
                ),
                pinningSupported = intent.getBooleanExtra(EXTRA_PINNING_SUPPORTED, false),
                canCreate = intent.getBooleanExtra(EXTRA_CAN_CREATE, false),
                canCreateDedicatedProfile = intent.getBooleanExtra(
                    EXTRA_CAN_CREATE_DEDICATED_PROFILE,
                    false,
                ),
                previewIcon = decodePreviewPng(intent.getByteArrayExtra(EXTRA_PREVIEW_ICON)),
            )
        }

        fun resultIntent(submission: SiteCapsuleEditorSubmission): Intent = Intent().apply {
            putExtra(EXTRA_RESULT_EXISTING_ID, submission.existingId)
            putExtra(EXTRA_RESULT_SOURCE_TAB_ID, submission.sourceTabId)
            putExtra(EXTRA_RESULT_NAME, submission.name)
            putExtra(EXTRA_RESULT_URL, submission.startUrl)
            putExtra(EXTRA_RESULT_PROFILE_ID, submission.selectedProfileId)
            putExtra(EXTRA_RESULT_CREATE_DEDICATED, submission.createDedicatedProfile)
            putExtra(EXTRA_RESULT_DEDICATED_EMOJI, submission.dedicatedEmoji)
            putExtra(EXTRA_RESULT_ISOLATED, submission.isolatedStorageRequested)
            putExtra(EXTRA_RESULT_NAVIGATION, submission.navigationMode.wireValue)
            putExtra(EXTRA_RESULT_CHROME, submission.chromeMode.wireValue)
            putExtra(EXTRA_RESULT_ICON, submission.iconMode.wireValue)
            submission.sourceFavicon?.toPreviewPng()?.let {
                putExtra(EXTRA_RESULT_SOURCE_FAVICON, it)
            }
        }

        private fun Intent.safeString(key: String, maxLength: Int): String? =
            getStringExtra(key)?.takeIf { it.length <= maxLength }

        private fun decodePreviewPng(bytes: ByteArray?): Bitmap? {
            val boundedBytes = bytes?.takeIf { it.size <= MAX_PREVIEW_BYTES } ?: return null
            val bitmap = BitmapFactory.decodeByteArray(boundedBytes, 0, boundedBytes.size)
                ?: return null
            return if (
                bitmap.width <= MAX_PREVIEW_DIMENSION &&
                bitmap.height <= MAX_PREVIEW_DIMENSION
            ) {
                bitmap
            } else {
                bitmap.recycle()
                null
            }
        }

        private fun Bitmap.toPreviewPng(): ByteArray? {
            if (isRecycled || width <= 0 || height <= 0) return null
            val scale = minOf(
                1f,
                MAX_PREVIEW_DIMENSION.toFloat() / maxOf(width, height).toFloat(),
            )
            val preview = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    this,
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                this
            }
            val bytes = ByteArrayOutputStream().use { output ->
                if (preview.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    output.toByteArray().takeIf { it.size <= MAX_PREVIEW_BYTES }
                } else {
                    null
                }
            }
            if (preview !== this) preview.recycle()
            return bytes
        }
    }
}
