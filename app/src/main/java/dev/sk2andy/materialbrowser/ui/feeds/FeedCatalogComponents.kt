package dev.sk2andy.materialbrowser.ui.feeds

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.feeds.FeedCatalogKind
import dev.sk2andy.materialbrowser.feeds.FeedCatalogSource
import java.net.URI
import java.util.Locale

internal data class FeedCatalogTopic(val slug: String, val label: Int, val symbol: String)

internal val feedCatalogTopics =
    listOf(
        FeedCatalogTopic("technology", R.string.feed_catalog_technology, "⌘"),
        FeedCatalogTopic("science", R.string.feed_catalog_science, "✳"),
        FeedCatalogTopic("design", R.string.feed_catalog_design, "◒"),
        FeedCatalogTopic("food", R.string.feed_catalog_food, "♧"),
        FeedCatalogTopic("travel", R.string.feed_catalog_travel, "↗"),
        FeedCatalogTopic("environment", R.string.feed_catalog_nature, "❋"),
    )

@Composable
internal fun FeedCatalogHero(modifier: Modifier = Modifier) {
    Surface(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        BoxWithConstraints {
            val showArtwork = maxWidth >= 340.dp && LocalDensity.current.fontScale <= 1.3f
            Row(
                Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.feed_catalog_eyebrow),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        stringResource(R.string.feed_catalog_headline),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        stringResource(R.string.feed_catalog_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                if (showArtwork) FeedCatalogGlobe(Modifier.size(80.dp).clearAndSetSemantics {})
            }
        }
    }
}

@Composable
internal fun FeedCatalogGlobe(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface.copy(alpha = .5f)
    Canvas(modifier) {
        val inset = size.minDimension * .08f
        val diameter = size.minDimension - 2 * inset
        drawCircle(surface, diameter / 2, center)
        val stroke = Stroke(width = 1.8.dp.toPx())
        drawCircle(color, diameter / 2, center, style = stroke)
        drawOval(
            color,
            Offset(size.width * .3f, inset),
            Size(size.width * .4f, diameter),
            style = stroke,
        )
        drawLine(color, Offset(inset, center.y), Offset(size.width - inset, center.y), stroke.width)
        drawLine(
            color,
            Offset(size.width * .15f, size.height * .3f),
            Offset(size.width * .85f, size.height * .3f),
            stroke.width,
        )
        drawLine(
            color,
            Offset(size.width * .15f, size.height * .7f),
            Offset(size.width * .85f, size.height * .7f),
            stroke.width,
        )
    }
}

@Composable
internal fun FeedCatalogTopicCard(
    topic: FeedCatalogTopic,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val background =
        when (index % 3) {
            0 -> colors.primaryContainer
            1 -> colors.surfaceContainerHigh
            else -> colors.tertiaryContainer
        }
    val foreground =
        when (index % 3) {
            0 -> colors.onPrimaryContainer
            1 -> colors.onSurface
            else -> colors.onTertiaryContainer
        }
    Surface(
        onClick = onClick,
        modifier = modifier.testTag("feed_catalog_topic_${topic.slug}"),
        shape = MaterialTheme.shapes.large,
        color = background,
        contentColor = foreground,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                topic.symbol,
                Modifier.align(Alignment.End).clearAndSetSemantics {},
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                stringResource(topic.label),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedCatalogSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Same native Search anatomy as LibrarySearchBar. Catalog queries have an explicit submit
    // contract.
    SearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = { onSearch() },
                expanded = false,
                onExpandedChange = {},
                placeholder = {
                    Text(
                        stringResource(R.string.feed_catalog_search_hint),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    Row {
                        if (query.isNotEmpty())
                            IconButton(
                                onClick = { onQueryChange("") },
                                modifier = Modifier.testTag("feed_catalog_clear"),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    stringResource(R.string.feed_catalog_clear),
                                )
                            }
                        IconButton(
                            onClick = onSearch,
                            enabled = query.isNotBlank(),
                            modifier = Modifier.testTag("feed_catalog_submit"),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                stringResource(R.string.feed_catalog_search),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                modifier = Modifier.testTag("feed_catalog_search"),
            )
        },
        expanded = false,
        onExpandedChange = {},
        windowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxWidth(),
    ) {}
}

@Composable
internal fun FeedCatalogSourceCard(
    source: FeedCatalogSource,
    subscribed: Boolean,
    displayLocale: Locale,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val language =
        source.language
            .takeIf { it.isNotBlank() }
            ?.let { Locale.forLanguageTag(it.replace('_', '-')).getDisplayLanguage(displayLocale) }
            ?.takeIf { it.isNotBlank() } ?: stringResource(R.string.feed_catalog_language_unknown)
    val domain = rememberCatalogDomain(source.siteUrl.ifBlank { source.feedUrl })
    Surface(
        onClick = onPreview,
        modifier = modifier.fillMaxWidth().testTag("feed_catalog_source_${source.slug}"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    Modifier.size(44.dp).clearAndSetSemantics {},
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            source.title.codePoints().findFirst().orElse('?'.code).let {
                                String(Character.toChars(it))
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        source.title,
                        style =
                            MaterialTheme.typography.titleMedium.copy(
                                textDirection = TextDirection.Content
                            ),
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        domain,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (subscribed)
                    Icon(
                        Icons.Default.Check,
                        stringResource(R.string.feed_catalog_subscribed),
                        tint = MaterialTheme.colorScheme.primary,
                    )
            }
            Text(
                source.description.ifBlank { stringResource(R.string.feed_catalog_no_description) },
                style =
                    MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                language +
                    " · " +
                    stringResource(
                        if (source.kind == FeedCatalogKind.News) R.string.feed_catalog_news
                        else R.string.feed_catalog_blogs
                    ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = onPreview,
                modifier =
                    Modifier.align(Alignment.End).testTag("feed_catalog_preview_${source.slug}"),
            ) {
                Text(stringResource(R.string.feed_catalog_preview))
                Spacer(Modifier.size(8.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun rememberCatalogDomain(url: String): String =
    androidx.compose.runtime.remember(url) {
        runCatching { URI(url).host?.removePrefix("www.") }.getOrNull()
    } ?: stringResource(R.string.feed_catalog_website_unknown)

@Composable
internal fun FeedCatalogMessage(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FeedCatalogGlobe(Modifier.size(60.dp).clearAndSetSemantics {})
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        action()
    }
}
