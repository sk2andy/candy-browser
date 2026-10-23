package dev.sk2andy.materialbrowser.ui.feeds

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.shared.ui.LocalCandyBrowserChromeEffects

/** Expressive masks belong to images; stable rectangular hit areas keep every label legible. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FeedCatalogTopicCard(
    topic: FeedCatalogTopic,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageSize: Dp = 80.dp,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduceMotion = LocalCandyBrowserChromeEffects.current.reducesMotion()
    val progress by
        animateFloatAsState(
            if (pressed) 1f else 0f,
            animationSpec =
                if (reduceMotion) snap() else MaterialTheme.motionScheme.fastSpatialSpec(),
            label = "Feed topic image shape",
        )
    val morph =
        remember(index) {
            val shape =
                when (index % 6) {
                    0 -> MaterialShapes.Arch
                    1 -> MaterialShapes.Gem
                    2 -> MaterialShapes.Slanted
                    3 -> MaterialShapes.Cookie4Sided
                    4 -> MaterialShapes.Fan
                    else -> MaterialShapes.Triangle
                }
            Morph(shape, MaterialShapes.Square)
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
        shape = MaterialTheme.shapes.extraLarge,
        color =
            when (index % 3) {
                0 -> colors.primaryContainer
                1 -> colors.tertiaryContainer
                else -> colors.secondaryContainer
            },
        contentColor =
            when (index % 3) {
                0 -> colors.onPrimaryContainer
                1 -> colors.onTertiaryContainer
                else -> colors.onSecondaryContainer
            },
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier.size(imageSize)
                        .align(Alignment.CenterHorizontally)
                        .clip(TopicImageShape(morph, progress.coerceIn(0f, 1f))),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(topic.label),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
            }
        }
    }
}

private class TopicImageShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = Path()
        val cubics = morph.asCubics(progress)
        cubics.firstOrNull()?.let { first ->
            path.moveTo(first.anchor0X * size.width, first.anchor0Y * size.height)
            cubics.forEach { cubic ->
                path.cubicTo(
                    cubic.control0X * size.width,
                    cubic.control0Y * size.height,
                    cubic.control1X * size.width,
                    cubic.control1Y * size.height,
                    cubic.anchor1X * size.width,
                    cubic.anchor1Y * size.height,
                )
            }
            path.close()
        }
        return Outline.Generic(path)
    }
}
