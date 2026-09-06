package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.sk2andy.materialbrowser.shared.ui.AddressLoadCapsuleFeedback as SharedAddressLoadCapsuleFeedback

internal typealias AddressLoadFeedbackMode =
    dev.sk2andy.materialbrowser.shared.ui.AddressLoadFeedbackMode
internal typealias AddressLoadFeedbackState =
    dev.sk2andy.materialbrowser.shared.ui.AddressLoadFeedbackState
internal typealias AddressLoadSegment =
    dev.sk2andy.materialbrowser.shared.ui.AddressLoadSegment
internal typealias AddressLoadCapsuleRules =
    dev.sk2andy.materialbrowser.shared.ui.AddressLoadCapsuleRules

@Composable
internal fun AddressLoadCapsuleFeedback(
    tabId: String,
    isLoading: Boolean,
    progressPercent: Int,
    morphProgress: Float,
    morphTargetSizePx: Float,
    sourceCornerRadiusPx: Float? = null,
    modifier: Modifier = Modifier,
) {
    SharedAddressLoadCapsuleFeedback(
        tabId = tabId,
        isLoading = isLoading,
        progressPercent = progressPercent,
        morphProgress = morphProgress,
        morphTargetSizePx = morphTargetSizePx,
        sourceCornerRadiusPx = sourceCornerRadiusPx,
        modifier = modifier,
    )
}
