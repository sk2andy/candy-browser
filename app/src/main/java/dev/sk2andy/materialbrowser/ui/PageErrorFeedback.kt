package dev.sk2andy.materialbrowser.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import kotlin.math.roundToInt

internal sealed interface PageErrorFeedbackState {
    val message: String?

    data class Hidden(override val message: String? = null) : PageErrorFeedbackState

    data class Error(override val message: String) : PageErrorFeedbackState

    data class Retrying(override val message: String) : PageErrorFeedbackState
}

internal data class PageErrorRetryTransition(
    val state: PageErrorFeedbackState,
    val shouldReload: Boolean,
    val emitConfirmHaptic: Boolean,
)

internal object PageErrorFeedbackRules {
    fun observe(
        current: PageErrorFeedbackState,
        error: String?,
        isLoading: Boolean,
    ): PageErrorFeedbackState = when {
        error != null -> PageErrorFeedbackState.Error(error)
        isLoading -> PageErrorFeedbackState.Hidden(current.message)
        current is PageErrorFeedbackState.Retrying -> current
        else -> PageErrorFeedbackState.Hidden(current.message)
    }

    fun requestRetry(current: PageErrorFeedbackState): PageErrorRetryTransition =
        if (current is PageErrorFeedbackState.Error) {
            PageErrorRetryTransition(
                state = PageErrorFeedbackState.Retrying(current.message),
                shouldReload = true,
                emitConfirmHaptic = true,
            )
        } else {
            PageErrorRetryTransition(
                state = current,
                shouldReload = false,
                emitConfirmHaptic = false,
            )
        }
}

internal object PageErrorFeedbackTestTags {
    const val Card = "page_error_card"
    const val Retry = "page_error_retry"
    const val RetryProgress = "page_error_retry_progress"
}

private object PageErrorMotion {
    const val ENTER_DURATION_MILLIS = 240
    const val EXIT_DURATION_MILLIS = 160
    const val FADE_IN_DURATION_MILLIS = 160
    const val FADE_OUT_DURATION_MILLIS = 120

    fun enterOffset(fullHeight: Int): Int =
        (fullHeight * 0.34f).roundToInt().coerceIn(0, fullHeight)

    fun exitOffset(fullHeight: Int): Int =
        (fullHeight * 0.18f).roundToInt().coerceIn(0, fullHeight)
}

@Composable
internal fun PageErrorFeedback(
    state: PageErrorFeedbackState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val retrying = state is PageErrorFeedbackState.Retrying
    val retryingLabel = stringResource(R.string.action_retrying)
    AnimatedVisibility(
        visible = state !is PageErrorFeedbackState.Hidden,
        modifier = modifier,
        enter = fadeIn(tween(PageErrorMotion.FADE_IN_DURATION_MILLIS)) +
            slideInVertically(
                animationSpec = tween(
                    PageErrorMotion.ENTER_DURATION_MILLIS,
                    easing = FastOutSlowInEasing,
                ),
                initialOffsetY = PageErrorMotion::enterOffset,
            ),
        exit = fadeOut(tween(PageErrorMotion.FADE_OUT_DURATION_MILLIS)) +
            slideOutVertically(
                animationSpec = tween(
                    PageErrorMotion.EXIT_DURATION_MILLIS,
                    easing = FastOutSlowInEasing,
                ),
                targetOffsetY = PageErrorMotion::exitOffset,
            ),
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .testTag(PageErrorFeedbackTestTags.Card)
                .semantics {
                    liveRegion = LiveRegionMode.Assertive
                    if (retrying) stateDescription = retryingLabel
                },
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.error_page_unreachable),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(8.dp))
                Text(state.message.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onRetry,
                    enabled = state is PageErrorFeedbackState.Error,
                    modifier = Modifier.testTag(PageErrorFeedbackTestTags.Retry),
                ) {
                    if (retrying) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(18.dp)
                                    .progressSemantics()
                                    .testTag(PageErrorFeedbackTestTags.RetryProgress),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(retryingLabel)
                        }
                    } else {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }
        }
    }
}
