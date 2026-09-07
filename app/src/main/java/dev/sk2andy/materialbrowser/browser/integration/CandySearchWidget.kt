package dev.sk2andy.materialbrowser.browser.integration

import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.isSynced

internal data class CandySearchWidgetProfile(
    val profileId: String,
    val emoji: String,
)

internal data class CandySearchWidgetState(
    val profiles: List<CandySearchWidgetProfile>,
)

internal enum class CandySearchWidgetLayout {
    Compact,
    Medium,
    Large,
}

internal data class CandySearchWidgetSize(
    val minWidthDp: Int,
    val minHeightDp: Int,
    val layout: CandySearchWidgetLayout,
)

internal object CandySearchWidgetRules {
    val responsiveSizes = listOf(
        CandySearchWidgetSize(
            minWidthDp = 140,
            minHeightDp = 52,
            layout = CandySearchWidgetLayout.Compact,
        ),
        CandySearchWidgetSize(
            minWidthDp = 200,
            minHeightDp = 52,
            layout = CandySearchWidgetLayout.Medium,
        ),
        CandySearchWidgetSize(
            minWidthDp = 270,
            minHeightDp = 52,
            layout = CandySearchWidgetLayout.Large,
        ),
    )

    private const val MAX_VISIBLE_PROFILES = 2

    fun state(
        profiles: List<BrowserProfile>,
        profilesEnabled: Boolean,
    ): CandySearchWidgetState = CandySearchWidgetState(
        profiles = if (profilesEnabled) {
            profiles.asSequence()
                .filterNot(BrowserProfile::isSynced)
                .map { profile ->
                    CandySearchWidgetProfile(
                        profileId = profile.id,
                        emoji = profile.emoji,
                    )
                }
                .take(MAX_VISIBLE_PROFILES)
                .toList()
        } else {
            emptyList()
        },
    )
}
