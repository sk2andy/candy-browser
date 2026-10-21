package dev.sk2andy.materialbrowser.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowInsetsControllerCompat
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.findActivity
import dev.sk2andy.materialbrowser.reader.ReaderExtractionResult
import dev.sk2andy.materialbrowser.reader.ReaderLibraryRepository
import dev.sk2andy.materialbrowser.reader.ReaderSpeechController
import dev.sk2andy.materialbrowser.shared.ui.ReaderStudioIcon
import dev.sk2andy.materialbrowser.shared.ui.ReaderStudioLabel
import dev.sk2andy.materialbrowser.shared.ui.ReaderStudioResources
import dev.sk2andy.materialbrowser.shared.ui.ReaderStudioScreen as SharedReaderStudioScreen
import dev.sk2andy.materialbrowser.shared.ui.ReaderStudioTestTags as SharedReaderStudioTestTags

internal typealias ReaderStudioTestTags = SharedReaderStudioTestTags

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ReaderStudioScreen(
    result: ReaderExtractionResult?,
    sourceUrl: String,
    isPrivate: Boolean,
    repository: ReaderLibraryRepository,
    sourceProfileId: String? = null,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onOpenOriginal: (String) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val context = LocalContext.current
    val backState = rememberNativeScreenBackState()
    val entryProgress = remember(sourceUrl, isPrivate) { Animatable(0f) }
    val entrySpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    LaunchedEffect(entryProgress) {
        entryProgress.animateTo(1f, entrySpec)
    }
    Box(
        modifier = Modifier.predictiveBackSurface(
            progress = backState.progress.value,
            swipeEdgeSign = backState.swipeEdgeSign,
        ).graphicsLayer {
            alpha = entryProgress.value
            translationY = 8.dp.toPx() * (1f - entryProgress.value)
        },
    ) {
        SharedReaderStudioScreen(
            result = result,
            sourceUrl = sourceUrl,
            isPrivate = isPrivate,
            repository = remember(repository, sourceProfileId) { repository.forProfile(sourceProfileId) },
            resources = AndroidReaderStudioResources,
            speechFactory = { ReaderSpeechController(context) },
            speechOptions = { speech ->
                if (speech is ReaderSpeechController) ReaderSpeechOptions(speech)
            },
            onRetry = onRetry,
            onDismiss = onDismiss,
            onOpenOriginal = onOpenOriginal,
            onOpenLink = onOpenLink,
            backHandler = { enabled, onBack ->
                NativeScreenBackHandler(state = backState, enabled = enabled, onBack = onBack)
            },
            systemBarAppearance = { darkIcons -> ReaderSystemBarAppearance(darkIcons) },
        )
    }
}

internal object AndroidReaderStudioResources : ReaderStudioResources {
    @Composable
    override fun text(label: ReaderStudioLabel, value: Int?): String = when (label) {
        ReaderStudioLabel.Settings -> stringResource(R.string.reader_settings)
        ReaderStudioLabel.CompactControls -> stringResource(R.string.reader_compact_controls)
        ReaderStudioLabel.ExpandControls -> stringResource(R.string.reader_expand_controls)
        ReaderStudioLabel.HideControls -> stringResource(R.string.reader_hide_controls)
        ReaderStudioLabel.HiddenControlsHint -> stringResource(R.string.reader_hidden_controls_hint)
        ReaderStudioLabel.ContinueReading -> stringResource(R.string.reader_continue_reading)
        ReaderStudioLabel.FontSerif -> stringResource(R.string.reader_font_serif)
        ReaderStudioLabel.FontSans -> stringResource(R.string.reader_font_sans)
        ReaderStudioLabel.Close -> stringResource(R.string.reader_close)
        ReaderStudioLabel.Title -> stringResource(R.string.reader_studio_title)
        ReaderStudioLabel.Article -> stringResource(R.string.reader_article)
        ReaderStudioLabel.Original -> stringResource(R.string.reader_original)
        ReaderStudioLabel.OfflineCount ->
            stringResource(R.string.reader_offline_count, requireNotNull(value))
        ReaderStudioLabel.Extracting -> stringResource(R.string.reader_extracting)
        ReaderStudioLabel.ExtractionFailedTitle ->
            stringResource(R.string.reader_extraction_failed_title)
        ReaderStudioLabel.ExtractionUnsupported ->
            stringResource(R.string.reader_extraction_unsupported)
        ReaderStudioLabel.ExtractionEmpty -> stringResource(R.string.reader_extraction_empty)
        ReaderStudioLabel.ExtractionInvalid -> stringResource(R.string.reader_extraction_invalid)
        ReaderStudioLabel.Retry -> stringResource(R.string.reader_retry)
        ReaderStudioLabel.SaveOffline -> stringResource(R.string.reader_save_offline)
        ReaderStudioLabel.OfflineShort -> stringResource(R.string.reader_offline_short)
        ReaderStudioLabel.ThemeSystem -> stringResource(R.string.reader_theme_system)
        ReaderStudioLabel.ThemeLight -> stringResource(R.string.reader_theme_light)
        ReaderStudioLabel.ThemePaper -> stringResource(R.string.reader_theme_paper)
        ReaderStudioLabel.ThemeNight -> stringResource(R.string.reader_theme_night)
        ReaderStudioLabel.FontDecrease -> stringResource(R.string.reader_font_decrease)
        ReaderStudioLabel.FontIncrease -> stringResource(R.string.reader_font_increase)
        ReaderStudioLabel.AlignmentStart -> stringResource(R.string.reader_alignment_start)
        ReaderStudioLabel.AlignmentJustified ->
            stringResource(R.string.reader_alignment_justified)
        ReaderStudioLabel.SpeechStart -> stringResource(R.string.reader_speech_start)
        ReaderStudioLabel.SpeechResume -> stringResource(R.string.reader_speech_resume)
        ReaderStudioLabel.SpeechPause -> stringResource(R.string.reader_speech_pause)
        ReaderStudioLabel.SpeechStop -> stringResource(R.string.reader_speech_stop)
        ReaderStudioLabel.PrivateNotice -> stringResource(R.string.reader_private_notice)
        ReaderStudioLabel.OfflineLibrary -> stringResource(R.string.reader_offline_library)
        ReaderStudioLabel.OfflineEmpty -> stringResource(R.string.reader_offline_empty)
        ReaderStudioLabel.ProgressPercent ->
            stringResource(R.string.reader_progress_percent, requireNotNull(value))
        ReaderStudioLabel.DeleteSnapshot -> stringResource(R.string.reader_delete_snapshot)
    }

    @Composable
    override fun icon(
        icon: ReaderStudioIcon,
        modifier: Modifier,
        contentDescription: String?,
    ) {
        Icon(
            painter = painterResource(
                when (icon) {
                    ReaderStudioIcon.Download -> R.drawable.ic_reader_download
                    ReaderStudioIcon.FontDecrease -> R.drawable.ic_reader_font_decrease
                    ReaderStudioIcon.FontIncrease -> R.drawable.ic_reader_font_increase
                    ReaderStudioIcon.AlignmentStart -> R.drawable.ic_reader_align_start
                    ReaderStudioIcon.AlignmentJustified -> R.drawable.ic_reader_align_justify
                    ReaderStudioIcon.Pause -> R.drawable.ic_reader_pause
                    ReaderStudioIcon.Stop -> R.drawable.ic_reader_stop
                },
            ),
            contentDescription = contentDescription,
            modifier = modifier,
        )
    }
}

@Composable
private fun ReaderSystemBarAppearance(darkIcons: Boolean) {
    val view = LocalView.current
    val window = view.context.findActivity()?.window ?: return
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    DisposableEffect(window) {
        val previousStatus = controller.isAppearanceLightStatusBars
        val previousNavigation = controller.isAppearanceLightNavigationBars
        onDispose {
            controller.isAppearanceLightStatusBars = previousStatus
            controller.isAppearanceLightNavigationBars = previousNavigation
        }
    }
    SideEffect {
        controller.isAppearanceLightStatusBars = darkIcons
        controller.isAppearanceLightNavigationBars = darkIcons
    }
}
