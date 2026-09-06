package dev.sk2andy.materialbrowser.ui

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.reader.ReaderExtractionResult
import dev.sk2andy.materialbrowser.reader.ReaderLibraryRepository
import dev.sk2andy.materialbrowser.reader.ReaderSpeechController
import dev.sk2andy.materialbrowser.shared.ui.ReaderStudioIcon
import dev.sk2andy.materialbrowser.shared.ui.ReaderStudioLabel
import dev.sk2andy.materialbrowser.shared.ui.ReaderStudioResources
import dev.sk2andy.materialbrowser.shared.ui.ReaderStudioScreen as SharedReaderStudioScreen
import dev.sk2andy.materialbrowser.shared.ui.ReaderStudioTestTags as SharedReaderStudioTestTags

internal typealias ReaderStudioTestTags = SharedReaderStudioTestTags

@Composable
internal fun ReaderStudioScreen(
    result: ReaderExtractionResult?,
    sourceUrl: String,
    isPrivate: Boolean,
    repository: ReaderLibraryRepository,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onOpenOriginal: (String) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val context = LocalContext.current
    SharedReaderStudioScreen(
        result = result,
        sourceUrl = sourceUrl,
        isPrivate = isPrivate,
        repository = repository,
        resources = AndroidReaderStudioResources,
        speechFactory = { ReaderSpeechController(context) },
        onRetry = onRetry,
        onDismiss = onDismiss,
        onOpenOriginal = onOpenOriginal,
        onOpenLink = onOpenLink,
    )
}

private object AndroidReaderStudioResources : ReaderStudioResources {
    @Composable
    override fun text(label: ReaderStudioLabel, value: Int?): String = when (label) {
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
