package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sk2andy.materialbrowser.reader.ReaderBlock
import dev.sk2andy.materialbrowser.reader.ReaderBlockKind
import dev.sk2andy.materialbrowser.reader.ReaderDocument
import dev.sk2andy.materialbrowser.reader.ReaderExtractionFailure
import dev.sk2andy.materialbrowser.reader.ReaderExtractionResult
import dev.sk2andy.materialbrowser.reader.ReaderLibraryRules
import dev.sk2andy.materialbrowser.reader.ReaderLibraryState
import dev.sk2andy.materialbrowser.reader.ReaderLibraryDataSource
import dev.sk2andy.materialbrowser.reader.ReaderSettings
import dev.sk2andy.materialbrowser.reader.ReaderSnapshot
import dev.sk2andy.materialbrowser.reader.ReaderSpeech
import dev.sk2andy.materialbrowser.reader.ReaderSpeechState
import dev.sk2andy.materialbrowser.reader.ReaderSpeechRules
import dev.sk2andy.materialbrowser.reader.ReaderSpeechStatus
import dev.sk2andy.materialbrowser.reader.ReaderTheme
import dev.sk2andy.materialbrowser.reader.ReaderTextAlignment
import kotlinx.coroutines.delay

object ReaderStudioTestTags {
    const val Screen = "reader_studio_screen"
    const val Loading = "reader_studio_loading"
    const val Error = "reader_studio_error"
    const val Article = "reader_studio_article"
    const val Progress = "reader_studio_progress"
    const val Save = "reader_studio_save"
    const val Library = "reader_studio_library"
    const val PrivateNotice = "reader_studio_private_notice"
    const val SpeechPlay = "reader_studio_speech_play"
    const val SpeechPause = "reader_studio_speech_pause"
    const val SpeechStop = "reader_studio_speech_stop"
    const val SpeechTransport = "reader_studio_speech_transport"
    const val ThemeSegmented = "reader_studio_theme_segmented"
    const val FontSegmented = "reader_studio_font_segmented"
    const val AlignmentSegmented = "reader_studio_alignment_segmented"
}

enum class ReaderStudioLabel {
    Close,
    Title,
    Article,
    Original,
    OfflineCount,
    Extracting,
    ExtractionFailedTitle,
    ExtractionUnsupported,
    ExtractionEmpty,
    ExtractionInvalid,
    Retry,
    SaveOffline,
    OfflineShort,
    ThemeSystem,
    ThemePaper,
    ThemeNight,
    FontDecrease,
    FontIncrease,
    AlignmentStart,
    AlignmentJustified,
    SpeechStart,
    SpeechResume,
    SpeechPause,
    SpeechStop,
    PrivateNotice,
    OfflineLibrary,
    OfflineEmpty,
    ProgressPercent,
    DeleteSnapshot,
}

enum class ReaderStudioIcon {
    Download,
    FontDecrease,
    FontIncrease,
    AlignmentStart,
    AlignmentJustified,
    Pause,
    Stop,
}

interface ReaderStudioResources {
    @Composable
    fun text(label: ReaderStudioLabel, value: Int? = null): String

    @Composable
    fun icon(
        icon: ReaderStudioIcon,
        modifier: Modifier,
        contentDescription: String?,
    )
}

internal object DefaultReaderStudioResources : ReaderStudioResources {
    @Composable
    override fun text(label: ReaderStudioLabel, value: Int?): String = when (label) {
        ReaderStudioLabel.Close -> "Close Reader Studio"
        ReaderStudioLabel.Title -> "Reader Studio"
        ReaderStudioLabel.Article -> "Article"
        ReaderStudioLabel.Original -> "Original"
        ReaderStudioLabel.OfflineCount -> "Offline (${requireNotNull(value)})"
        ReaderStudioLabel.Extracting -> "Preparing article on this device…"
        ReaderStudioLabel.ExtractionFailedTitle -> "Article could not be prepared"
        ReaderStudioLabel.ExtractionUnsupported ->
            "Reader Studio works with HTTP and HTTPS pages."
        ReaderStudioLabel.ExtractionEmpty ->
            "No sufficiently readable article text was found. The original page is unchanged."
        ReaderStudioLabel.ExtractionInvalid ->
            "The page changed or returned invalid content. Try again or return to the original."
        ReaderStudioLabel.Retry -> "Try again"
        ReaderStudioLabel.SaveOffline -> "Save offline"
        ReaderStudioLabel.OfflineShort -> "Offline"
        ReaderStudioLabel.ThemeSystem -> "System"
        ReaderStudioLabel.ThemePaper -> "Paper"
        ReaderStudioLabel.ThemeNight -> "Night"
        ReaderStudioLabel.FontDecrease -> "Decrease text size"
        ReaderStudioLabel.FontIncrease -> "Increase text size"
        ReaderStudioLabel.AlignmentStart -> "Ragged"
        ReaderStudioLabel.AlignmentJustified -> "Justified"
        ReaderStudioLabel.SpeechStart -> "Read aloud"
        ReaderStudioLabel.SpeechResume -> "Resume"
        ReaderStudioLabel.SpeechPause -> "Pause"
        ReaderStudioLabel.SpeechStop -> "Stop"
        ReaderStudioLabel.PrivateNotice ->
            "Private reading stays in memory. Progress, settings, and offline copies are not saved."
        ReaderStudioLabel.OfflineLibrary -> "Offline articles"
        ReaderStudioLabel.OfflineEmpty -> "No offline articles saved yet."
        ReaderStudioLabel.ProgressPercent -> "${requireNotNull(value)}% read"
        ReaderStudioLabel.DeleteSnapshot -> "Delete offline article"
    }

    @Composable
    override fun icon(
        icon: ReaderStudioIcon,
        modifier: Modifier,
        contentDescription: String?,
    ) {
        Icon(
            imageVector = when (icon) {
                ReaderStudioIcon.Download -> Icons.Filled.Download
                ReaderStudioIcon.FontDecrease -> Icons.Filled.Remove
                ReaderStudioIcon.FontIncrease -> Icons.Filled.Add
                ReaderStudioIcon.AlignmentStart -> Icons.Filled.FormatAlignLeft
                ReaderStudioIcon.AlignmentJustified -> Icons.Filled.FormatAlignJustify
                ReaderStudioIcon.Pause -> Icons.Filled.Pause
                ReaderStudioIcon.Stop -> Icons.Filled.Stop
            },
            contentDescription = contentDescription,
            modifier = modifier,
        )
    }
}

internal class SessionReaderLibraryDataSource : ReaderLibraryDataSource {
    private var state = ReaderLibraryState()
    private var nextSnapshotId = 1L

    override fun load(isPrivate: Boolean, onLoaded: (ReaderLibraryState) -> Unit) {
        onLoaded(ReaderLibraryRules.visibleState(state, isPrivate))
    }

    override fun updateSettings(
        settings: ReaderSettings,
        isPrivate: Boolean,
        onUpdated: (ReaderLibraryState) -> Unit,
    ) {
        state = ReaderLibraryRules.updateSettings(state, settings, isPrivate)
        onUpdated(ReaderLibraryRules.visibleState(state, isPrivate))
    }

    override fun updateProgress(sourceUrl: String, progress: Float, isPrivate: Boolean) {
        state = ReaderLibraryRules.updateProgress(state, sourceUrl, progress, isPrivate)
    }

    override fun saveSnapshot(
        document: ReaderDocument,
        progress: Float,
        isPrivate: Boolean,
        onUpdated: (ReaderLibraryState) -> Unit,
    ) {
        val snapshotId = nextSnapshotId++
        state = ReaderLibraryRules.saveSnapshot(
            state = state,
            snapshot = ReaderSnapshot(
                id = "reader-$snapshotId",
                document = document,
                progress = progress.coerceIn(0f, 1f),
                savedAtMillis = snapshotId,
            ),
            isPrivate = isPrivate,
        )
        onUpdated(ReaderLibraryRules.visibleState(state, isPrivate))
    }

    override fun deleteSnapshot(
        snapshotId: String,
        isPrivate: Boolean,
        onUpdated: (ReaderLibraryState) -> Unit,
    ) {
        state = ReaderLibraryRules.deleteSnapshot(state, snapshotId, isPrivate)
        onUpdated(ReaderLibraryRules.visibleState(state, isPrivate))
    }
}

internal class UnavailableReaderSpeech : ReaderSpeech {
    override var state = ReaderSpeechState(status = ReaderSpeechStatus.Unavailable)
        private set

    override fun play(content: String) = Unit

    override fun pause() = Unit

    override fun stop() = Unit

    override fun close() {
        state = ReaderSpeechState(status = ReaderSpeechStatus.Closed)
    }
}

@Composable
fun ReaderStudioScreen(
    result: ReaderExtractionResult?,
    sourceUrl: String,
    isPrivate: Boolean,
    repository: ReaderLibraryDataSource,
    resources: ReaderStudioResources,
    speechFactory: () -> ReaderSpeech,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onOpenOriginal: (String) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    var library by remember(isPrivate) { mutableStateOf(ReaderLibraryState()) }
    var activeDocument by remember(result) {
        mutableStateOf((result as? ReaderExtractionResult.Success)?.document)
    }
    var activeSnapshot by remember { mutableStateOf<ReaderSnapshot?>(null) }
    var libraryVisible by remember { mutableStateOf(false) }
    var libraryLoaded by remember(isPrivate) { mutableStateOf(isPrivate) }
    var sessionSettings by remember(library.settings, isPrivate) {
        mutableStateOf(library.settings)
    }
    val colors = readerColors(sessionSettings.theme)
    LaunchedEffect(result, isPrivate) {
        libraryLoaded = false
        repository.load(isPrivate) { loaded ->
            library = loaded
            libraryLoaded = true
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag(ReaderStudioTestTags.Screen),
        color = colors.background,
        contentColor = colors.content,
    ) {
        Column(modifier = Modifier.safeDrawingPadding()) {
            ReaderStudioHeader(
                resources = resources,
                libraryVisible = libraryVisible,
                isPrivate = isPrivate,
                snapshotCount = library.snapshots.size,
                onDismiss = onDismiss,
                onOpenOriginal = {
                    onOpenOriginal(activeDocument?.sourceUrl ?: sourceUrl)
                },
                onLibrary = { libraryVisible = !libraryVisible },
            )
            if (libraryVisible && !isPrivate) {
                ReaderLibraryContent(
                    resources = resources,
                    snapshots = library.snapshots,
                    progressByUrl = library.progressByUrl,
                    colors = colors,
                    onOpen = { snapshot ->
                        activeSnapshot = snapshot
                        activeDocument = snapshot.document
                        libraryVisible = false
                    },
                    onDelete = { snapshot ->
                        if (activeSnapshot?.id == snapshot.id) activeSnapshot = null
                        repository.deleteSnapshot(snapshot.id, isPrivate = false) { updated ->
                            library = updated
                        }
                    },
                )
            } else {
                when {
                    result == null && activeDocument == null -> ReaderLoading(resources, colors)
                    result is ReaderExtractionResult.Failure && activeDocument == null ->
                        ReaderError(
                            resources,
                            result.reason,
                            colors,
                            onRetry,
                            onOpenOriginal = { onOpenOriginal(sourceUrl) },
                        )
                    activeDocument != null -> ReaderArticle(
                        document = checkNotNull(activeDocument),
                        snapshotProgress = activeSnapshot?.progress,
                        isPrivate = isPrivate,
                        settings = sessionSettings,
                        library = library,
                        libraryLoaded = libraryLoaded,
                        repository = repository,
                        resources = resources,
                        speechFactory = speechFactory,
                        colors = colors,
                        onSettingsChanged = { settings ->
                            sessionSettings = settings
                            if (!isPrivate) {
                                repository.updateSettings(
                                    settings,
                                    isPrivate = false,
                                ) { updated ->
                                    library = updated
                                }
                            }
                        },
                        onLibraryChanged = { updatedLibrary ->
                            library = updatedLibrary
                            activeSnapshot = library.snapshots.firstOrNull {
                                it.document.sourceUrl == activeDocument?.sourceUrl
                            }
                        },
                        onOpenLink = onOpenLink,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderStudioHeader(
    resources: ReaderStudioResources,
    libraryVisible: Boolean,
    isPrivate: Boolean,
    snapshotCount: Int,
    onDismiss: () -> Unit,
    onOpenOriginal: () -> Unit,
    onLibrary: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onDismiss) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = resources.text(ReaderStudioLabel.Close),
            )
        }
        Text(
            resources.text(ReaderStudioLabel.Title),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        if (!isPrivate) {
            TextButton(
                onClick = onLibrary,
                modifier = Modifier.testTag(ReaderStudioTestTags.Library),
            ) {
                Text(
                    if (libraryVisible) {
                        resources.text(ReaderStudioLabel.Article)
                    } else {
                        resources.text(ReaderStudioLabel.OfflineCount, snapshotCount)
                    },
                )
            }
        }
        TextButton(onClick = onOpenOriginal) {
            Text(resources.text(ReaderStudioLabel.Original))
        }
    }
}

@Composable
private fun ReaderLoading(
    resources: ReaderStudioResources,
    colors: ReaderColors,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(ReaderStudioTestTags.Loading),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = colors.accent)
            Spacer(Modifier.height(16.dp))
            Text(resources.text(ReaderStudioLabel.Extracting))
        }
    }
}

@Composable
private fun ReaderError(
    resources: ReaderStudioResources,
    failure: ReaderExtractionFailure,
    colors: ReaderColors,
    onRetry: () -> Unit,
    onOpenOriginal: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag(ReaderStudioTestTags.Error),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = RoundedCornerShape(32.dp), color = colors.card) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    resources.text(ReaderStudioLabel.ExtractionFailedTitle),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    resources.text(
                        when (failure) {
                            ReaderExtractionFailure.UnsupportedPage ->
                                ReaderStudioLabel.ExtractionUnsupported
                            ReaderExtractionFailure.EmptyArticle -> ReaderStudioLabel.ExtractionEmpty
                            ReaderExtractionFailure.InvalidResponse ->
                                ReaderStudioLabel.ExtractionInvalid
                        },
                    ),
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onOpenOriginal) {
                        Text(resources.text(ReaderStudioLabel.Original))
                    }
                    Button(onClick = onRetry) {
                        Text(resources.text(ReaderStudioLabel.Retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderArticle(
    document: ReaderDocument,
    snapshotProgress: Float?,
    isPrivate: Boolean,
    settings: ReaderSettings,
    library: ReaderLibraryState,
    libraryLoaded: Boolean,
    repository: ReaderLibraryDataSource,
    resources: ReaderStudioResources,
    speechFactory: () -> ReaderSpeech,
    colors: ReaderColors,
    onSettingsChanged: (ReaderSettings) -> Unit,
    onLibraryChanged: (ReaderLibraryState) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val scrollState = rememberScrollState()
    val speech = remember(document.sourceUrl) { speechFactory() }
    var restored by remember(document.sourceUrl) { mutableStateOf(false) }
    val progress = ReaderLibraryRules.progress(scrollState.value, scrollState.maxValue)

    DisposableEffect(speech) { onDispose(speech::close) }
    LaunchedEffect(document.sourceUrl, scrollState.maxValue, libraryLoaded) {
        if (restored || !libraryLoaded || scrollState.maxValue <= 0) return@LaunchedEffect
        val savedProgress = ReaderLibraryRules.resumeProgress(
            state = library,
            snapshotProgress = snapshotProgress,
            sourceUrl = document.sourceUrl,
        )
        scrollState.scrollTo((scrollState.maxValue * savedProgress).toInt())
        restored = true
    }
    LaunchedEffect(scrollState.isScrollInProgress, restored) {
        if (restored && !scrollState.isScrollInProgress) {
            delay(350)
            repository.updateProgress(document.sourceUrl, progress, isPrivate)
        }
    }
    DisposableEffect(document.sourceUrl, isPrivate) {
        onDispose {
            if (isPrivate) return@onDispose
            val finalProgress = ReaderLibraryRules.progress(scrollState.value, scrollState.maxValue)
            repository.updateProgress(document.sourceUrl, finalProgress, isPrivate)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(colors.background, colors.card.copy(alpha = 0.34f)),
                ),
            ),
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .testTag(ReaderStudioTestTags.Progress),
            color = colors.accent,
            trackColor = colors.accent.copy(alpha = 0.16f),
        )
        ReaderControls(
            resources = resources,
            settings = settings,
            isPrivate = isPrivate,
            speechStatus = speech.state.status,
            speechExcerpt = ReaderSpeechRules.currentExcerpt(
                document.speechText,
                speech.state.characterOffset,
            ),
            colors = colors,
            onSettingsChanged = onSettingsChanged,
            onPlay = { speech.play(document.speechText) },
            onPause = speech::pause,
            onStop = speech::stop,
            onSave = {
                repository.saveSnapshot(document, progress, isPrivate, onLibraryChanged)
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .testTag(ReaderStudioTestTags.Article),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.article,
                shape = RoundedCornerShape(32.dp),
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
            ) {
                Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp)) {
                    Surface(
                        color = colors.accent.copy(alpha = 0.12f),
                        contentColor = colors.accent,
                        shape = CircleShape,
                    ) {
                        Text(
                            document.siteName,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        document.title,
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 30.sp * settings.fontScale,
                            lineHeight = 36.sp * settings.fontScale,
                        ),
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        document.sourceUrl,
                        color = colors.muted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(22.dp))
                    document.blocks.forEach { block ->
                        ReaderBlockContent(
                            block = block,
                            fontScale = settings.fontScale,
                            textAlignment = settings.textAlignment,
                            colors = colors,
                            onOpenLink = onOpenLink,
                        )
                    }
                }
            }
            Spacer(Modifier.height(36.dp))
        }
    }
}

@Composable
private fun ReaderControls(
    resources: ReaderStudioResources,
    settings: ReaderSettings,
    isPrivate: Boolean,
    speechStatus: ReaderSpeechStatus,
    speechExcerpt: String,
    colors: ReaderColors,
    onSettingsChanged: (ReaderSettings) -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onSave: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = colors.card,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 5.dp,
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReaderFontSizeSegmented(
                resources = resources,
                settings = settings,
                colors = colors,
                onSettingsChanged = onSettingsChanged,
                modifier = Modifier.weight(1f),
            )
            ReaderAlignmentSegmented(
                resources = resources,
                settings = settings,
                colors = colors,
                onSettingsChanged = onSettingsChanged,
                modifier = Modifier.weight(1f),
            )
            if (!isPrivate) {
                val saveDescription = resources.text(ReaderStudioLabel.SaveOffline)
                val showSaveLabel = LocalDensity.current.fontScale < 1.6f
                FilledTonalButton(
                    onClick = onSave,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(ReaderStudioTestTags.Save),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colors.accent,
                        contentColor = colors.onAccent,
                    ),
                ) {
                    resources.icon(
                        icon = ReaderStudioIcon.Download,
                        modifier = Modifier.size(20.dp),
                        contentDescription = saveDescription.takeUnless { showSaveLabel },
                    )
                    if (showSaveLabel) {
                        Spacer(Modifier.size(6.dp))
                        Text(resources.text(ReaderStudioLabel.OfflineShort))
                    }
                }
            }
        }
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ReaderStudioTestTags.ThemeSegmented),
        ) {
            ReaderTheme.entries.forEachIndexed { index, theme ->
                SegmentedButton(
                    modifier = Modifier.weight(1f),
                    selected = settings.theme == theme,
                    onClick = { onSettingsChanged(settings.copy(theme = theme)) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = ReaderTheme.entries.size,
                    ),
                    label = {
                        Text(
                            resources.text(
                                when (theme) {
                                    ReaderTheme.System -> ReaderStudioLabel.ThemeSystem
                                    ReaderTheme.Paper -> ReaderStudioLabel.ThemePaper
                                    ReaderTheme.Night -> ReaderStudioLabel.ThemeNight
                                },
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = colors.accent,
                        activeContentColor = colors.onAccent,
                        activeBorderColor = colors.accent,
                        inactiveContainerColor = Color.Transparent,
                        inactiveContentColor = colors.content,
                        inactiveBorderColor = colors.muted,
                    ),
                )
            }
        }
        ReaderSpeechTransport(
            resources = resources,
            status = speechStatus,
            excerpt = speechExcerpt,
            colors = colors,
            onPlay = onPlay,
            onPause = onPause,
            onStop = onStop,
        )
        if (isPrivate) {
            Text(
                resources.text(ReaderStudioLabel.PrivateNotice),
                modifier = Modifier.testTag(ReaderStudioTestTags.PrivateNotice),
                color = colors.muted,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        }
    }
}

@Composable
private fun ReaderFontSizeSegmented(
    resources: ReaderStudioResources,
    settings: ReaderSettings,
    colors: ReaderColors,
    onSettingsChanged: (ReaderSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val decreaseDescription = resources.text(ReaderStudioLabel.FontDecrease)
    val increaseDescription = resources.text(ReaderStudioLabel.FontIncrease)
    val actions = listOf(
        Triple(ReaderStudioIcon.FontDecrease, decreaseDescription) {
            onSettingsChanged(settings.copy(fontScale = settings.fontScale - 0.1f))
        },
        Triple(ReaderStudioIcon.FontIncrease, increaseDescription) {
            onSettingsChanged(settings.copy(fontScale = settings.fontScale + 0.1f))
        },
    )
    SingleChoiceSegmentedButtonRow(
        modifier = modifier.testTag(ReaderStudioTestTags.FontSegmented),
    ) {
        actions.forEachIndexed { index, (icon, description, action) ->
            SegmentedButton(
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                    onClick {
                        action()
                        true
                    }
                },
                selected = false,
                onClick = action,
                shape = SegmentedButtonDefaults.itemShape(index, actions.size),
                icon = {},
                label = {
                    resources.icon(
                        icon = icon,
                        modifier = Modifier.size(if (index == 0) 18.dp else 20.dp),
                        contentDescription = null,
                    )
                },
                colors = readerSegmentedColors(colors),
            )
        }
    }
}

@Composable
private fun ReaderAlignmentSegmented(
    resources: ReaderStudioResources,
    settings: ReaderSettings,
    colors: ReaderColors,
    onSettingsChanged: (ReaderSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = modifier.testTag(ReaderStudioTestTags.AlignmentSegmented),
    ) {
        ReaderTextAlignment.entries.forEachIndexed { index, alignment ->
            val description = resources.text(
                when (alignment) {
                    ReaderTextAlignment.Start -> ReaderStudioLabel.AlignmentStart
                    ReaderTextAlignment.Justified -> ReaderStudioLabel.AlignmentJustified
                },
            )
            SegmentedButton(
                selected = settings.textAlignment == alignment,
                onClick = { onSettingsChanged(settings.copy(textAlignment = alignment)) },
                shape = SegmentedButtonDefaults.itemShape(index, ReaderTextAlignment.entries.size),
                icon = {},
                label = {
                    resources.icon(
                        icon = when (alignment) {
                            ReaderTextAlignment.Start -> ReaderStudioIcon.AlignmentStart
                            ReaderTextAlignment.Justified -> ReaderStudioIcon.AlignmentJustified
                        },
                        contentDescription = description,
                        modifier = Modifier.size(20.dp),
                    )
                },
                colors = readerSegmentedColors(colors),
            )
        }
    }
}

@Composable
private fun readerSegmentedColors(colors: ReaderColors) = SegmentedButtonDefaults.colors(
    activeContainerColor = colors.accent,
    activeContentColor = colors.onAccent,
    activeBorderColor = colors.accent,
    inactiveContainerColor = Color.Transparent,
    inactiveContentColor = colors.content,
    inactiveBorderColor = colors.muted,
)

@Composable
private fun ReaderSpeechTransport(
    resources: ReaderStudioResources,
    status: ReaderSpeechStatus,
    excerpt: String,
    colors: ReaderColors,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
) {
    val isSpeaking = status == ReaderSpeechStatus.Speaking
    val canPlay = status == ReaderSpeechStatus.Ready || status == ReaderSpeechStatus.Paused
    val canStop = isSpeaking || status == ReaderSpeechStatus.Paused
    val primaryActionDescription = resources.text(
        when {
            isSpeaking -> ReaderStudioLabel.SpeechPause
            status == ReaderSpeechStatus.Paused -> ReaderStudioLabel.SpeechResume
            else -> ReaderStudioLabel.SpeechStart
        },
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ReaderStudioTestTags.SpeechTransport),
        color = colors.article.copy(alpha = 0.82f),
        contentColor = colors.content,
        shape = CircleShape,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(
                onClick = if (isSpeaking) onPause else onPlay,
                enabled = isSpeaking || canPlay,
                modifier = Modifier
                    .size(52.dp)
                    .testTag(
                        if (isSpeaking) {
                            ReaderStudioTestTags.SpeechPause
                        } else {
                            ReaderStudioTestTags.SpeechPlay
                        },
                    ),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                    disabledContainerColor = colors.muted.copy(alpha = 0.18f),
                    disabledContentColor = colors.muted,
                ),
            ) {
                if (isSpeaking) {
                    resources.icon(
                        icon = ReaderStudioIcon.Pause,
                        modifier = Modifier.size(24.dp),
                        contentDescription = primaryActionDescription,
                    )
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = primaryActionDescription)
                }
            }
            Text(
                excerpt,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(
                onClick = onStop,
                enabled = canStop,
                modifier = Modifier
                    .size(48.dp)
                    .testTag(ReaderStudioTestTags.SpeechStop),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = colors.accent,
                    disabledContentColor = colors.muted.copy(alpha = 0.45f),
                ),
            ) {
                resources.icon(
                    icon = ReaderStudioIcon.Stop,
                    modifier = Modifier.size(24.dp),
                    contentDescription = resources.text(ReaderStudioLabel.SpeechStop),
                )
            }
        }
    }
}

@Composable
private fun ReaderBlockContent(
    block: ReaderBlock,
    fontScale: Float,
    textAlignment: ReaderTextAlignment,
    colors: ReaderColors,
    onOpenLink: (String) -> Unit,
) {
    val justify = ReaderLibraryRules.shouldJustify(block.kind, textAlignment)
    val style = when (block.kind) {
        ReaderBlockKind.Heading -> MaterialTheme.typography.headlineSmall.copy(
            fontSize = (24 - block.level.coerceAtLeast(1)).sp * fontScale,
            lineHeight = 29.sp * fontScale,
            fontWeight = FontWeight.Bold,
        )
        ReaderBlockKind.Quote -> MaterialTheme.typography.bodyLarge.copy(
            fontSize = 18.sp * fontScale,
            lineHeight = 29.sp * fontScale,
            fontWeight = FontWeight.Medium,
        )
        ReaderBlockKind.ListItem,
        ReaderBlockKind.Paragraph,
        -> MaterialTheme.typography.bodyLarge.copy(
            fontSize = 18.sp * fontScale,
            lineHeight = 29.sp * fontScale,
        )
    }
    val prefix = if (block.kind == ReaderBlockKind.ListItem) "•  " else ""
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (block.kind == ReaderBlockKind.Quote) colors.card else Color.Transparent,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (block.kind == ReaderBlockKind.Quote) 16.dp else 0.dp,
                    vertical = 10.dp,
                ),
        ) {
            Text(
                prefix + block.text,
                modifier = if (block.kind == ReaderBlockKind.Heading) {
                    Modifier.semantics { heading() }
                } else {
                    Modifier
                },
                style = style.copy(
                    textAlign = if (justify) TextAlign.Justify else TextAlign.Start,
                    hyphens = if (justify) Hyphens.Auto else Hyphens.None,
                ),
            )
            block.links.forEach { link ->
                Surface(
                    onClick = { onOpenLink(link.url) },
                    modifier = Modifier.padding(top = 8.dp),
                    color = colors.accent.copy(alpha = 0.12f),
                    contentColor = colors.accent,
                    shape = CircleShape,
                ) {
                    Text(
                        "↗ ${link.label}",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderLibraryContent(
    resources: ReaderStudioResources,
    snapshots: List<ReaderSnapshot>,
    progressByUrl: Map<String, Float>,
    colors: ReaderColors,
    onOpen: (ReaderSnapshot) -> Unit,
    onDelete: (ReaderSnapshot) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
    ) {
        Text(
            resources.text(ReaderStudioLabel.OfflineLibrary),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(16.dp))
        if (snapshots.isEmpty()) {
            Text(resources.text(ReaderStudioLabel.OfflineEmpty), color = colors.muted)
        }
        snapshots.forEach { snapshot ->
            Surface(
                onClick = { onOpen(snapshot) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                color = colors.card,
                shape = RoundedCornerShape(24.dp),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(snapshot.document.title, fontWeight = FontWeight.Bold, maxLines = 2)
                        Text(snapshot.document.siteName, color = colors.muted)
                        Text(
                            resources.text(
                                ReaderStudioLabel.ProgressPercent,
                                (
                                    progressByUrl[snapshot.document.sourceUrl]
                                        ?: snapshot.progress
                                    ).times(100).toInt(),
                            ),
                            color = colors.muted,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    IconButton(onClick = { onDelete(snapshot) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = resources.text(ReaderStudioLabel.DeleteSnapshot),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

private data class ReaderColors(
    val background: Color,
    val content: Color,
    val muted: Color,
    val card: Color,
    val article: Color,
    val accent: Color,
    val onAccent: Color,
)

@Composable
private fun readerColors(theme: ReaderTheme): ReaderColors = when (theme) {
    ReaderTheme.System -> ReaderColors(
        background = MaterialTheme.colorScheme.surfaceContainerLow,
        content = MaterialTheme.colorScheme.onSurface,
        muted = MaterialTheme.colorScheme.onSurfaceVariant,
        card = MaterialTheme.colorScheme.surfaceContainerHigh,
        article = MaterialTheme.colorScheme.surfaceContainerLowest,
        accent = MaterialTheme.colorScheme.primary,
        onAccent = MaterialTheme.colorScheme.onPrimary,
    )
    ReaderTheme.Paper -> ReaderColors(
        background = Color(0xFFFFF4D6),
        content = Color(0xFF322A1D),
        muted = Color(0xFF736247),
        card = Color(0xFFF5E3B7),
        article = Color(0xFFFFFAEA),
        accent = Color(0xFF85591A),
        onAccent = Color.White,
    )
    ReaderTheme.Night -> ReaderColors(
        background = Color(0xFF111317),
        content = Color(0xFFE5E2E8),
        muted = Color(0xFFAAA6B0),
        card = Color(0xFF25272D),
        article = Color(0xFF191B20),
        accent = Color(0xFFD8B7FF),
        onAccent = Color(0xFF281338),
    )
}
