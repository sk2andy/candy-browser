package dev.sk2andy.materialbrowser.browser

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import dev.sk2andy.materialbrowser.ProfileWallpaperEditorActivity

data class ProfileWallpaperEditorRequest(
    val profileId: String,
    val target: ProfileWallpaperTarget,
    val wallpaper: ProfileWallpaper?,
)

data class ProfileWallpaperEditorSubmission(
    val profileId: String,
    val target: ProfileWallpaperTarget,
    val wallpaper: ProfileWallpaper?,
)

class ProfileWallpaperEditorContract :
    ActivityResultContract<ProfileWallpaperEditorRequest, ProfileWallpaperEditorSubmission?>() {
    override fun createIntent(context: Context, input: ProfileWallpaperEditorRequest): Intent =
        Intent(context, ProfileWallpaperEditorActivity::class.java).apply {
            putExtra(EXTRA_PROFILE_ID, input.profileId)
            putExtra(EXTRA_TARGET, input.target.wireValue)
            input.wallpaper?.let { wallpaper ->
                val safe = ProfileWallpaperRules.sanitize(wallpaper)
                putExtra(EXTRA_HAS_WALLPAPER, true)
                putExtra(EXTRA_ZOOM, safe.zoom)
                putExtra(EXTRA_PAN_X, safe.normalizedPanX)
                putExtra(EXTRA_PAN_Y, safe.normalizedPanY)
            }
        }

    override fun parseResult(resultCode: Int, intent: Intent?): ProfileWallpaperEditorSubmission? {
        if (resultCode != Activity.RESULT_OK || intent == null) return null
        val profileId = intent.getStringExtra(EXTRA_RESULT_PROFILE_ID)
            ?.takeIf(ProfileWallpaperRules::isSupportedLocalProfileId)
            ?: return null
        val target = ProfileWallpaperTarget.fromWireValue(
            intent.getStringExtra(EXTRA_RESULT_TARGET),
        ) ?: return null
        val wallpaper = if (intent.getBooleanExtra(EXTRA_RESULT_REMOVED, false)) {
            null
        } else {
            ProfileWallpaperRules.sanitize(
                ProfileWallpaper(
                    zoom = intent.getFloatExtra(EXTRA_RESULT_ZOOM, 1f),
                    normalizedPanX = intent.getFloatExtra(EXTRA_RESULT_PAN_X, 0f),
                    normalizedPanY = intent.getFloatExtra(EXTRA_RESULT_PAN_Y, 0f),
                ),
            )
        }
        return ProfileWallpaperEditorSubmission(profileId, target, wallpaper)
    }

    companion object {
        private const val EXTRA_PROFILE_ID = "profile_wallpaper.profile_id"
        private const val EXTRA_TARGET = "profile_wallpaper.target"
        private const val EXTRA_HAS_WALLPAPER = "profile_wallpaper.has_wallpaper"
        private const val EXTRA_ZOOM = "profile_wallpaper.zoom"
        private const val EXTRA_PAN_X = "profile_wallpaper.pan_x"
        private const val EXTRA_PAN_Y = "profile_wallpaper.pan_y"
        private const val EXTRA_RESULT_PROFILE_ID = "profile_wallpaper.result.profile_id"
        private const val EXTRA_RESULT_TARGET = "profile_wallpaper.result.target"
        private const val EXTRA_RESULT_REMOVED = "profile_wallpaper.result.removed"
        private const val EXTRA_RESULT_ZOOM = "profile_wallpaper.result.zoom"
        private const val EXTRA_RESULT_PAN_X = "profile_wallpaper.result.pan_x"
        private const val EXTRA_RESULT_PAN_Y = "profile_wallpaper.result.pan_y"

        fun requestFrom(intent: Intent): ProfileWallpaperEditorRequest? {
            val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
                ?.takeIf(ProfileWallpaperRules::isSupportedLocalProfileId)
                ?: return null
            val target = ProfileWallpaperTarget.fromWireValue(
                intent.getStringExtra(EXTRA_TARGET),
            ) ?: return null
            val wallpaper = if (intent.getBooleanExtra(EXTRA_HAS_WALLPAPER, false)) {
                ProfileWallpaperRules.sanitize(
                    ProfileWallpaper(
                        zoom = intent.getFloatExtra(EXTRA_ZOOM, 1f),
                        normalizedPanX = intent.getFloatExtra(EXTRA_PAN_X, 0f),
                        normalizedPanY = intent.getFloatExtra(EXTRA_PAN_Y, 0f),
                    ),
                )
            } else {
                null
            }
            return ProfileWallpaperEditorRequest(profileId, target, wallpaper)
        }

        fun resultIntent(submission: ProfileWallpaperEditorSubmission): Intent = Intent().apply {
            putExtra(EXTRA_RESULT_PROFILE_ID, submission.profileId)
            putExtra(EXTRA_RESULT_TARGET, submission.target.wireValue)
            putExtra(EXTRA_RESULT_REMOVED, submission.wallpaper == null)
            submission.wallpaper?.let(ProfileWallpaperRules::sanitize)?.let { wallpaper ->
                putExtra(EXTRA_RESULT_ZOOM, wallpaper.zoom)
                putExtra(EXTRA_RESULT_PAN_X, wallpaper.normalizedPanX)
                putExtra(EXTRA_RESULT_PAN_Y, wallpaper.normalizedPanY)
            }
        }
    }
}
