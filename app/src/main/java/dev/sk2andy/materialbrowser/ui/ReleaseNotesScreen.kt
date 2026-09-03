package dev.sk2andy.materialbrowser.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.ReleaseNotesBlock
import dev.sk2andy.materialbrowser.browser.ReleaseNotesDecoration
import dev.sk2andy.materialbrowser.browser.ReleaseNotesDocument
import dev.sk2andy.materialbrowser.browser.ReleaseNotesInline
import dev.sk2andy.materialbrowser.browser.ReleaseNotesInlineStyle
import dev.sk2andy.materialbrowser.ui.theme.CandyPink
import dev.sk2andy.materialbrowser.ui.theme.CandyPurple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object ReleaseNotesTestTags {
    const val Screen = "release_notes_screen"
    const val Content = "release_notes_content"
    const val Done = "release_notes_done"
    const val Close = "release_notes_close"
    const val Image = "release_notes_image"
}

@Composable
internal fun ReleaseNotesScreen(
    versionName: String,
    document: ReleaseNotesDocument,
    onDone: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    BackHandler(onBack = onDone)
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag(ReleaseNotesTestTags.Screen),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            ReleaseNotesHero(
                versionName = versionName,
                title = document.title.text,
                onClose = onDone,
            )
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .testTag(ReleaseNotesTestTags.Content),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 24.dp,
                    top = 24.dp,
                    end = 24.dp,
                    bottom = 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(document.blocks.drop(1)) { block ->
                    ReleaseNotesBlock(
                        block = block,
                        onOpenLink = onOpenLink,
                    )
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp,
            ) {
                Button(
                    onClick = onDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .height(54.dp)
                        .testTag(ReleaseNotesTestTags.Done),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(
                        stringResource(R.string.release_notes_done),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReleaseNotesHero(
    versionName: String,
    title: String,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        CandyPurple.copy(alpha = 0.30f),
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                        CandyPink.copy(alpha = 0.24f),
                    ),
                ),
            )
            .padding(horizontal = 24.dp, vertical = 22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(78.dp),
                shape = RoundedCornerShape(25.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shadowElevation = 8.dp,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground_art),
                    contentDescription = null,
                    modifier = Modifier.padding(9.dp),
                )
            }
            Spacer(Modifier.width(18.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    stringResource(R.string.release_notes_eyebrow),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.release_notes_version, versionName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.Top)
                    .testTag(ReleaseNotesTestTags.Close),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_symbol_close),
                    contentDescription = stringResource(R.string.cd_close_release_notes),
                )
            }
        }
    }
}

@Composable
private fun ReleaseNotesBlock(
    block: ReleaseNotesBlock,
    onOpenLink: (String) -> Unit,
) {
    when (block) {
        is ReleaseNotesBlock.Heading -> MarkdownText(
            content = block.content,
            style = when (block.level) {
                1 -> MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
                2 -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                else -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
            },
            onOpenLink = onOpenLink,
        )
        is ReleaseNotesBlock.Paragraph -> MarkdownText(
            content = block.content,
            style = MaterialTheme.typography.bodyLarge,
            onOpenLink = onOpenLink,
        )
        is ReleaseNotesBlock.ListItems -> Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            block.items.forEachIndexed { index, item ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = if (block.ordered) "${index + 1}." else "•",
                        modifier = Modifier.width(28.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    MarkdownText(
                        content = item,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        onOpenLink = onOpenLink,
                    )
                }
            }
        }
        is ReleaseNotesBlock.Quote -> Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f),
            shape = RoundedCornerShape(18.dp),
        ) {
            Row(modifier = Modifier.padding(18.dp)) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .heightIn(min = 28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Spacer(Modifier.width(14.dp))
                MarkdownText(
                    content = block.content,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                    onOpenLink = onOpenLink,
                )
            }
        }
        is ReleaseNotesBlock.Code -> Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                text = block.content,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
        }
        is ReleaseNotesBlock.Image -> ReleaseNotesImage(block)
        ReleaseNotesBlock.Divider -> HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
private fun ReleaseNotesImage(block: ReleaseNotesBlock.Image) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, key1 = block.assetPath) {
        value = withContext(Dispatchers.IO) {
            loadReleaseNotesImage(context, block.assetPath)
        }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
    ) {
        if (image != null) {
            Image(
                bitmap = requireNotNull(image),
                contentDescription = block.altText,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 360.dp)
                    .padding(10.dp)
                    .testTag(ReleaseNotesTestTags.Image),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            )
        } else {
            Text(
                text = block.altText,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp)
                    .padding(24.dp)
                    .semantics { contentDescription = block.altText },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun MarkdownText(
    content: ReleaseNotesInline,
    style: TextStyle,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val annotated = releaseNotesAnnotatedString(
        content = content,
        linkColor = MaterialTheme.colorScheme.primary,
        codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
    ClickableText(
        text = annotated,
        modifier = modifier,
        style = style.copy(color = MaterialTheme.colorScheme.onSurface),
        onClick = { offset ->
            annotated.getStringAnnotations(tag = LINK_TAG, start = offset, end = offset)
                .firstOrNull()
                ?.item
                ?.let(onOpenLink)
        },
    )
}

private fun loadReleaseNotesImage(
    context: Context,
    assetPath: String,
): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.assets.open(assetPath).use { input ->
        BitmapFactory.decodeStream(input, null, bounds)
    }
    require(bounds.outWidth in 1..MAX_RELEASE_NOTES_IMAGE_DIMENSION)
    require(bounds.outHeight in 1..MAX_RELEASE_NOTES_IMAGE_DIMENSION)
    require(bounds.outWidth.toLong() * bounds.outHeight <= MAX_RELEASE_NOTES_IMAGE_PIXELS)
    context.assets.open(assetPath).use { input ->
        BitmapFactory.decodeStream(input)?.asImageBitmap()
    }
}.getOrNull()

private fun releaseNotesAnnotatedString(
    content: ReleaseNotesInline,
    linkColor: androidx.compose.ui.graphics.Color,
    codeBackground: androidx.compose.ui.graphics.Color,
): AnnotatedString = buildAnnotatedString {
    append(content.text)
    content.decorations.forEach { decoration ->
        addStyle(
            style = decoration.spanStyle(linkColor, codeBackground),
            start = decoration.start,
            end = decoration.end,
        )
        if (decoration.style == ReleaseNotesInlineStyle.Link && decoration.target != null) {
            addStringAnnotation(
                tag = LINK_TAG,
                annotation = decoration.target,
                start = decoration.start,
                end = decoration.end,
            )
        }
    }
}

private fun ReleaseNotesDecoration.spanStyle(
    linkColor: androidx.compose.ui.graphics.Color,
    codeBackground: androidx.compose.ui.graphics.Color,
): SpanStyle = when (style) {
    ReleaseNotesInlineStyle.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
    ReleaseNotesInlineStyle.Italic -> SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
    ReleaseNotesInlineStyle.Code -> SpanStyle(
        fontFamily = FontFamily.Monospace,
        background = codeBackground,
    )
    ReleaseNotesInlineStyle.Link -> SpanStyle(
        color = linkColor,
        textDecoration = TextDecoration.Underline,
        fontWeight = FontWeight.Medium,
    )
}

private const val LINK_TAG = "release-notes-link"
private const val MAX_RELEASE_NOTES_IMAGE_DIMENSION = 4_096
private const val MAX_RELEASE_NOTES_IMAGE_PIXELS = 8_388_608L
