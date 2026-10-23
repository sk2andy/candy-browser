package dev.sk2andy.materialbrowser.ui.feeds

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.shared.ui.LocalCandyBrowserChromeEffects

/** A photo mosaic with a tall lead image, stacked details and a full-width panorama. */
@Composable
internal fun FeedCatalogTopics(columns: Int, onTopicSelected: (FeedCatalogTopic) -> Unit) {
    val gap = 10.dp
    val large = columns > 2
    val textScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    @Composable
    fun Tile(index: Int, height: Dp, modifier: Modifier = Modifier) {
        val topic = feedCatalogTopics[index]
        FeedCatalogTopicCard(
            topic = topic,
            index = index,
            onClick = { onTopicSelected(topic) },
            imageHeight = height,
            modifier =
                modifier.semantics {
                    traversalIndex =
                        if (columns == 1) index.toFloat()
                        else
                            when (index) {
                                4 -> 3f
                                3 -> 4f
                                else -> index.toFloat()
                            }
                },
        )
    }
    Column(
        Modifier.fillMaxWidth().semantics { isTraversalGroup = true },
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        if (columns == 1) {
            feedCatalogTopics.indices.forEach {
                Tile(it, 160.dp * textScale, Modifier.fillMaxWidth())
            }
        } else {
            val leadHeight = if (large) 300.dp else 214.dp
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Tile(0, leadHeight, Modifier.weight(1.8f))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                    Tile(1, (leadHeight - gap) / 2, Modifier.fillMaxWidth())
                    Tile(2, (leadHeight - gap) / 2, Modifier.fillMaxWidth())
                }
            }
            Tile(4, if (large) 136.dp else 92.dp, Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                val height = if (large) 184.dp else 128.dp
                Tile(3, height, Modifier.weight(1f))
                Tile(5, height, Modifier.weight(1.5f))
            }
        }
    }
}

/** Edge-to-edge photos with a contrast scrim beneath overlaid titles. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FeedCatalogTopicCard(
    topic: FeedCatalogTopic,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageHeight: Dp = 124.dp,
) {
    val density = LocalDensity.current
    val scrimHeight = with(density) { (108.dp * fontScale.coerceAtLeast(1f)).toPx() }
    val bottomTitle = index == 1 || index == 4
    val cardHeight = with(density) { imageHeight.toPx() }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduceMotion = LocalCandyBrowserChromeEffects.current.reducesMotion()
    val corner by
        animateDpAsState(
            if (pressed) 24.dp else 48.dp,
            animationSpec =
                if (reduceMotion) snap() else MaterialTheme.motionScheme.fastSpatialSpec(),
            label = "Feed topic corner",
        )
    val shape =
        when (index % 6) {
            0 -> RoundedCornerShape(12.dp, corner, 16.dp, 16.dp)
            1 -> RoundedCornerShape(corner, 12.dp, 16.dp, 16.dp)
            2 -> RoundedCornerShape(12.dp, 12.dp, 32.dp, 12.dp)
            3 -> RoundedCornerShape(28.dp, corner, 12.dp, 28.dp)
            4 -> RoundedCornerShape(corner, 12.dp, 28.dp, 12.dp)
            else -> RoundedCornerShape(12.dp, corner, 12.dp, 32.dp)
        }
    val image = ImageBitmap.imageResource(R.drawable.feed_topic_atlas)
    val cell = IntSize(image.width / 3, image.height / 2)
    val painter =
        remember(image, index) {
            BitmapPainter(
                image,
                IntOffset((index % 3) * cell.width, (index / 3) * cell.height),
                cell,
            )
        }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier.testTag("feed_catalog_topic_${topic.slug}"),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = Color.White,
    ) {
        Box(Modifier.fillMaxWidth().height(imageHeight)) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                Modifier.matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to
                                if (bottomTitle) Color.Transparent
                                else Color.Black.copy(alpha = .74f),
                            .5f to Color.Black.copy(alpha = .64f),
                            1f to
                                if (bottomTitle) Color.Black.copy(alpha = .74f)
                                else Color.Transparent,
                            startY = if (bottomTitle) cardHeight - scrimHeight else 0f,
                            endY = if (bottomTitle) cardHeight else scrimHeight,
                        )
                    )
            )
            Text(
                stringResource(topic.label),
                Modifier.align(if (bottomTitle) Alignment.BottomEnd else Alignment.TopStart)
                    .padding(14.dp),
                style =
                    if (index == 0 || index == 4) MaterialTheme.typography.titleLarge
                    else MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = if (bottomTitle) TextAlign.End else TextAlign.Start,
            )
        }
    }
}
