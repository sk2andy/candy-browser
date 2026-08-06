package dev.sk2andy.materialbrowser.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.ui.theme.CandyPink
import dev.sk2andy.materialbrowser.ui.theme.CandyPurple
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun CandySplashScreen(modifier: Modifier = Modifier) {
    val revealProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        revealProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 850,
                easing = FastOutSlowInEasing,
            ),
        )
    }
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("candy_splash"),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = revealProgress.value }
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                CandyPink.copy(alpha = 0.30f),
                                MaterialTheme.colorScheme.surface,
                                CandyPurple.copy(alpha = 0.24f),
                            ),
                            radius = 1_100f,
                        ),
                    ),
            )
            Box(
                modifier = Modifier.size(210.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .graphicsLayer {
                            val progress = revealProgress.value
                            alpha = ((progress - 0.12f) / 0.38f).coerceIn(0f, 1f)
                            translationX = 66.dp.toPx() * progress
                            translationY = -34.dp.toPx() * progress
                            rotationZ = -35f + 165f * progress
                            scaleX = 0.25f + 0.75f * progress
                            scaleY = scaleX
                        }
                        .clip(RoundedCornerShape(18.dp))
                        .background(CandyPink),
                )
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .graphicsLayer {
                            val progress = revealProgress.value
                            alpha = ((progress - 0.12f) / 0.38f).coerceIn(0f, 1f)
                            translationX = -68.dp.toPx() * progress
                            translationY = 38.dp.toPx() * progress
                            rotationZ = 220f * progress
                            scaleX = 0.25f + 0.75f * progress
                            scaleY = scaleX
                        }
                        .clip(CircleShape)
                        .background(CandyPurple),
                )
                Surface(
                    modifier = Modifier
                        .size(154.dp)
                        .graphicsLayer {
                            val progress = revealProgress.value
                            alpha = ((progress - 0.18f) / 0.42f).coerceIn(0f, 1f)
                            scaleX = 0.72f + 0.28f * progress
                            scaleY = scaleX
                        },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 12.dp,
                    shadowElevation = 22.dp,
                ) { }
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground_art),
                    contentDescription = null,
                    modifier = Modifier
                        .size(120.dp)
                        .testTag("candy_splash_icon")
                        .graphicsLayer {
                            val progress = revealProgress.value
                            val pulse = sin(progress * PI).toFloat()
                            rotationZ = 360f * progress
                            scaleX = 1f + pulse * 0.12f
                            scaleY = scaleX
                        },
                )
            }
        }
    }
}
