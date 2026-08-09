package dev.sk2andy.materialbrowser.ui

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.ui.theme.CandyPink
import dev.sk2andy.materialbrowser.ui.theme.CandyPurple
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class GestureOnboardingStep {
    SwitchTabs,
    OpenTabOverview,
    CloseTab,
}

internal object GestureOnboardingRules {
    fun isCompleted(
        step: GestureOnboardingStep,
        dragX: Float,
        dragY: Float,
        threshold: Float,
    ): Boolean {
        if (threshold <= 0f) return false
        return when (step) {
            GestureOnboardingStep.SwitchTabs ->
                dragX.absoluteValue >= threshold && dragX.absoluteValue > dragY.absoluteValue
            GestureOnboardingStep.OpenTabOverview,
            GestureOnboardingStep.CloseTab,
            -> dragY <= -threshold && dragY.absoluteValue > dragX.absoluteValue
        }
    }
}

@Composable
internal fun GestureOnboardingScreen(
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val steps = GestureOnboardingStep.entries
    var welcomeVisible by rememberSaveable { mutableStateOf(true) }
    var celebrationVisible by rememberSaveable { mutableStateOf(false) }
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var practiceWidthPx by remember { mutableFloatStateOf(0f) }
    var rootWidthPx by remember { mutableFloatStateOf(0f) }
    val lessonScrollState = rememberScrollState()
    val step = steps[stepIndex]
    val density = LocalDensity.current
    val threshold = with(density) {
        when (step) {
            GestureOnboardingStep.SwitchTabs ->
                if (rootWidthPx > 0f) {
                    rootWidthPx * AddressBarTabSwitchRules.DISTANCE_FRACTION
                } else {
                    72.dp.toPx()
                }
            GestureOnboardingStep.OpenTabOverview ->
                AddressBarGestureRules.OPEN_TABS_THRESHOLD_DP.dp.toPx()
            GestureOnboardingStep.CloseTab ->
                if (practiceWidthPx > 0f) {
                    ((practiceWidthPx / 0.53f) * 0.28f) *
                        TabDismissPhysics.DEFAULT_RESISTANCE_FRACTION
                } else {
                    44.dp.toPx()
                }
        }
    }
    val view = LocalView.current
    val stepAccessibilityDescription = stepDescription(step)
    val completeActionLabel = stringResource(R.string.onboarding_accessibility_complete_action)

    BackHandler(enabled = true) { }
    LaunchedEffect(welcomeVisible, celebrationVisible, step) {
        if (!welcomeVisible) lessonScrollState.scrollTo(0)
    }

    fun completeStep() {
        dragX = 0f
        dragY = 0f
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        if (stepIndex == steps.lastIndex) {
            celebrationVisible = true
        } else {
            stepIndex++
        }
    }

    fun isCompletionKey(key: Key): Boolean = when (step) {
        GestureOnboardingStep.SwitchTabs ->
            key == Key.DirectionLeft || key == Key.DirectionRight
        GestureOnboardingStep.OpenTabOverview,
        GestureOnboardingStep.CloseTab,
        -> key == Key.DirectionUp
    }

    val currentStep by rememberUpdatedState(step)
    val currentThreshold by rememberUpdatedState(threshold)
    val currentCompleteStep by rememberUpdatedState { completeStep() }

    val gestureModifier = Modifier
        .testTag("gesture_onboarding_${step.name}")
        .onSizeChanged {
            practiceWidthPx = it.width.toFloat()
        }
        .semantics {
            contentDescription = stepAccessibilityDescription
            customActions = listOf(
                CustomAccessibilityAction(completeActionLabel) {
                    completeStep()
                    true
                },
            )
        }
        .onPreviewKeyEvent { event ->
            val handlesEvent = isCompletionKey(event.key)
            if (handlesEvent && event.type == KeyEventType.KeyUp) completeStep()
            handlesEvent
        }
        .focusable()
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial,
                )
                down.consume()
                dragX = 0f
                dragY = 0f
                var lastPosition = down.position
                var released = false
                while (true) {
                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val delta = change.position - lastPosition
                    dragX += delta.x
                    dragY += delta.y
                    lastPosition = change.position
                    change.consume()
                    if (!change.pressed) {
                        released = true
                        break
                    }
                }
                if (released) {
                    if (
                        GestureOnboardingRules.isCompleted(
                            currentStep,
                            dragX,
                            dragY,
                            currentThreshold,
                        )
                    ) {
                        currentCompleteStep()
                    } else {
                        dragX = 0f
                        dragY = 0f
                    }
                } else {
                    dragX = 0f
                    dragY = 0f
                }
            }
        }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { rootWidthPx = it.width.toFloat() }
            .testTag("gesture_onboarding"),
        color = MaterialTheme.colorScheme.surface,
    ) {
        if (welcomeVisible) {
            GestureOnboardingWelcome(
                onStart = { welcomeVisible = false },
                onSkip = onCompleted,
            )
        } else if (celebrationVisible) {
            GestureOnboardingCelebration(onContinue = onCompleted)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                CandyPink.copy(alpha = 0.14f),
                                MaterialTheme.colorScheme.surface,
                                CandyPurple.copy(alpha = 0.18f),
                            ),
                        ),
                    )
                    .safeDrawingPadding()
                    .verticalScroll(lessonScrollState)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.Center,
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.onboarding_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(
                            R.string.onboarding_progress,
                            stepIndex + 1,
                            steps.size,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = onCompleted,
                        modifier = Modifier.testTag("gesture_onboarding_skip"),
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_skip),
                            color = gestureAccent(step),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { (stepIndex + 1f) / steps.size },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = gestureAccent(step),
                trackColor = gestureAccent(step).copy(alpha = 0.16f),
            )
            Spacer(Modifier.height(28.dp))
            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(100)) },
                label = "gesture-onboarding-copy",
            ) { currentStep ->
                Column {
                    Text(
                        text = stepTitle(currentStep),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stepDescription(currentStep),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(390.dp),
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
            ) {
                GesturePracticeArea(
                    step = step,
                    dragX = dragX,
                    dragY = dragY,
                    modifier = gestureModifier,
                )
            }
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                GestureDirectionBadge(step = step)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.onboarding_follow_pointer),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge,
                color = gestureAccent(step),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.onboarding_required_hint),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }
        }
    }
}

@Composable
private fun GestureOnboardingWelcome(
    onStart: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("gesture_onboarding_welcome")
            .background(
                Brush.verticalGradient(
                    listOf(
                        CandyPink.copy(alpha = 0.24f),
                        MaterialTheme.colorScheme.surface,
                        CandyPurple.copy(alpha = 0.24f),
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
        CandyWelcomeHero()
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_body),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text(
                    text = stringResource(R.string.onboarding_welcome_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(14.dp))
                WelcomeGestureRow(
                    symbol = "↔",
                    title = stepTitle(GestureOnboardingStep.SwitchTabs),
                    color = CandyPurple,
                )
                WelcomeGestureRow(
                    symbol = "↑",
                    title = stepTitle(GestureOnboardingStep.OpenTabOverview),
                    color = CandyPink,
                )
                WelcomeGestureRow(
                    symbol = "↑",
                    title = stepTitle(GestureOnboardingStep.CloseTab),
                    color = CandyPurple,
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("gesture_onboarding_start"),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CandyPurple,
                contentColor = Color.White,
            ),
        ) {
            Text(
                text = stringResource(R.string.onboarding_welcome_start),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
            Spacer(Modifier.height(12.dp))
        }
        TextButton(
            onClick = onSkip,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .safeDrawingPadding()
                .padding(top = 6.dp, end = 8.dp)
                .testTag("gesture_onboarding_skip"),
        ) {
            Text(
                text = stringResource(R.string.onboarding_skip),
                color = CandyPurple,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun GestureOnboardingCelebration(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val completionTitle = stringResource(R.string.onboarding_completion_title)
    val burstProgress = remember { Animatable(0f) }
    val exitProgress = remember { Animatable(0f) }
    var contentVisible by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val confettiTransition = rememberInfiniteTransition(label = "onboarding-confetti")
    val streamProgress by confettiTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_200, easing = LinearEasing),
        ),
        label = "onboarding-confetti-stream",
    )
    LaunchedEffect(Unit) {
        burstProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1_250, easing = FastOutSlowInEasing),
        )
    }
    LaunchedEffect(Unit) {
        delay(620)
        contentVisible = true
    }
    val confettiColors = listOf(
        CandyPink,
        CandyPurple,
        Color(0xFFFFC857),
        Color(0xFF2EC4B6),
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("gesture_onboarding_celebration")
            .graphicsLayer {
                val exit = exitProgress.value
                alpha = 1f - ((exit - 0.52f) / 0.48f).coerceIn(0f, 1f)
            }
            .semantics {
                paneTitle = completionTitle
                liveRegion = LiveRegionMode.Polite
            }
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        CandyPurple.copy(alpha = 0.22f),
                        MaterialTheme.colorScheme.surface,
                        CandyPink.copy(alpha = 0.20f),
                    ),
                ),
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val origin = Offset(size.width / 2f, size.height / 2f)
            val maxDistance = size.maxDimension * 0.72f
            val burst = burstProgress.value
            val burstAlpha = when {
                burst < 0.72f -> 1f
                else -> ((1f - burst) / 0.28f).coerceIn(0f, 1f)
            }
            val streamAlpha = ((burst - 0.32f) / 0.38f).coerceIn(0f, 1f)

            if (burst < 1f) {
                repeat(34) { index ->
                    val angle = Math.toRadians(((index * 137.5f) % 360f).toDouble())
                    val distance = burst * maxDistance * (0.55f + (index % 7) * 0.055f)
                    val center = Offset(
                        x = origin.x + cos(angle).toFloat() * distance,
                        y = origin.y + sin(angle).toFloat() * distance +
                            burst * burst * size.height * 0.12f,
                    )
                    drawConfettiPiece(
                        index = index,
                        center = center,
                        rotation = burst * 620f + index * 29f,
                        color = confettiColors[index % confettiColors.size].copy(
                            alpha = burstAlpha,
                        ),
                    )
                }
            }

            repeat(48) { index ->
                val emissionDelay = ((index * 23) % 100) / 100f
                val age = (streamProgress - emissionDelay + 1f) % 1f
                val angle = Math.toRadians(((index * 131f + 12f) % 360f).toDouble())
                val distance = age * maxDistance * (0.48f + (index % 9) * 0.045f)
                val center = Offset(
                    x = origin.x + cos(angle).toFloat() * distance,
                    y = origin.y + sin(angle).toFloat() * distance +
                        age * age * size.height * 0.16f,
                )
                val alpha = streamAlpha * when {
                    age < 0.08f -> age / 0.08f
                    age > 0.82f -> (1f - age) / 0.18f
                    else -> 1f
                }.coerceIn(0f, 1f)
                drawConfettiPiece(
                    index = index,
                    center = center,
                    rotation = streamProgress * 720f + index * 31f,
                    color = confettiColors[index % confettiColors.size].copy(alpha = alpha),
                )
            }
        }
        AnimatedVisibility(
            visible = contentVisible,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(durationMillis = 720)) +
                scaleIn(
                    initialScale = 0.92f,
                    animationSpec = tween(
                        durationMillis = 720,
                        easing = FastOutSlowInEasing,
                    ),
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
            Surface(
                modifier = Modifier.size(132.dp),
                shape = CircleShape,
                color = CandyPurple,
                shadowElevation = 18.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "✓",
                        modifier = Modifier.offset(y = (-3).dp),
                        color = Color.White,
                        fontSize = 64.sp,
                        lineHeight = 64.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(30.dp))
            Text(
                text = completionTitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.onboarding_completion_body),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(34.dp))
            Button(
                enabled = !finishing,
                onClick = {
                    if (!finishing) {
                        finishing = true
                        coroutineScope.launch {
                            exitProgress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    durationMillis = 720,
                                    easing = FastOutSlowInEasing,
                                ),
                            )
                            onContinue()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("gesture_onboarding_finish")
                    .graphicsLayer {
                        val exit = exitProgress.value
                        val buttonScale = when {
                            exit < 0.28f -> 1f - 0.18f * (exit / 0.28f)
                            exit < 0.58f ->
                                0.82f + 0.28f * ((exit - 0.28f) / 0.30f)
                            else -> 1.10f - 0.10f * ((exit - 0.58f) / 0.42f)
                        }
                        scaleX = buttonScale
                        scaleY = buttonScale
                    },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CandyPurple,
                    contentColor = Color.White,
                    disabledContainerColor = CandyPurple,
                    disabledContentColor = Color.White,
                ),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_completion_cta),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
}

private fun DrawScope.drawConfettiPiece(
    index: Int,
    center: Offset,
    rotation: Float,
    color: Color,
) {
    if (index % 3 == 0) {
        drawCircle(
            color = color,
            radius = 5.dp.toPx(),
            center = center,
        )
    } else {
        rotate(degrees = rotation, pivot = center) {
            drawRect(
                color = color,
                topLeft = Offset(
                    x = center.x - 3.dp.toPx(),
                    y = center.y - 8.dp.toPx(),
                ),
                size = Size(6.dp.toPx(), 16.dp.toPx()),
            )
        }
    }
}

@Composable
private fun CandyWelcomeHero(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "candy-welcome-hero")
    val floatOffset by transition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "candy-welcome-float",
    )

    Box(
        modifier = modifier.size(174.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 8.dp, y = 26.dp)
                .size(70.dp)
                .graphicsLayer { rotationZ = -14f + floatOffset }
                .clip(RoundedCornerShape(24.dp))
                .background(CandyPink.copy(alpha = 0.8f)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-4).dp, y = (-18).dp)
                .size(62.dp)
                .clip(CircleShape)
                .background(CandyPurple.copy(alpha = 0.8f)),
        )
        Surface(
            modifier = Modifier
                .size(126.dp)
                .graphicsLayer {
                    translationY = floatOffset
                    rotationZ = floatOffset * 0.25f
                },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 10.dp,
            shadowElevation = 18.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                CandyPink.copy(alpha = 0.18f),
                                CandyPurple.copy(alpha = 0.24f),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground_art),
                    contentDescription = null,
                    modifier = Modifier.size(94.dp),
                )
            }
        }
    }
}

@Composable
private fun WelcomeGestureRow(
    symbol: String,
    title: String,
    color: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = CircleShape,
            color = color,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = symbol,
                    modifier = Modifier.offset(y = (-2).dp),
                    color = Color.White,
                    fontSize = 20.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun GesturePracticeArea(
    step: GestureOnboardingStep,
    dragX: Float,
    dragY: Float,
    modifier: Modifier,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        when (step) {
            GestureOnboardingStep.SwitchTabs,
            GestureOnboardingStep.OpenTabOverview,
            -> AddressBarGesturePractice(step, dragX, dragY, modifier)
            GestureOnboardingStep.CloseTab ->
                CloseTabPractice(dragY, modifier)
        }
        GesturePointerGuide(
            step = step,
            userDragging = dragX.absoluteValue > 1f || dragY.absoluteValue > 1f,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun GesturePointerGuide(
    step: GestureOnboardingStep,
    userDragging: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "gesture-pointer-${step.name}")
    val loopProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_900, easing = LinearEasing),
        ),
        label = "gesture-pointer-progress",
    )
    val primary = gestureAccent(step)
    val surface = MaterialTheme.colorScheme.surface
    Canvas(
        modifier = modifier
            .testTag("gesture_onboarding_pointer_${step.name}"),
    ) {
        val (start, end) = when (step) {
            GestureOnboardingStep.SwitchTabs ->
                Offset(size.width * 0.74f, size.height * 0.84f) to
                    Offset(size.width * 0.28f, size.height * 0.84f)
            GestureOnboardingStep.OpenTabOverview ->
                Offset(size.width * 0.68f, size.height * 0.84f) to
                    Offset(size.width * 0.68f, size.height * 0.55f)
            GestureOnboardingStep.CloseTab ->
                Offset(size.width * 0.58f, size.height * 0.58f) to
                    Offset(size.width * 0.58f, size.height * 0.18f)
        }
        val rawTravel = ((loopProgress - 0.16f) / 0.62f).coerceIn(0f, 1f)
        val travel = FastOutSlowInEasing.transform(rawTravel)
        val loopAlpha = when {
            loopProgress < 0.12f -> loopProgress / 0.12f
            loopProgress > 0.86f -> (1f - loopProgress) / 0.14f
            else -> 1f
        }.coerceIn(0f, 1f)
        val guideAlpha = if (userDragging) 0f else loopAlpha
        val pointer = Offset(
            x = start.x + (end.x - start.x) * travel,
            y = start.y + (end.y - start.y) * travel,
        )
        drawLine(
            color = primary.copy(alpha = 0.38f * guideAlpha),
            start = start,
            end = end,
            strokeWidth = 3.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(
                intervals = floatArrayOf(10.dp.toPx(), 7.dp.toPx()),
                phase = -loopProgress * 24.dp.toPx(),
            ),
        )
        drawCircle(
            color = primary.copy(alpha = 0.16f * guideAlpha),
            radius = 25.dp.toPx() + 4.dp.toPx() * (1f - rawTravel),
            center = pointer,
        )
        drawCircle(
            color = surface.copy(alpha = guideAlpha),
            radius = 13.dp.toPx(),
            center = pointer,
        )
        drawCircle(
            color = primary.copy(alpha = guideAlpha),
            radius = 13.dp.toPx(),
            center = pointer,
            style = Stroke(width = 3.dp.toPx()),
        )
        drawCircle(
            color = primary.copy(alpha = guideAlpha),
            radius = 4.dp.toPx(),
            center = pointer,
        )
    }
}

@Composable
private fun AddressBarGesturePractice(
    step: GestureOnboardingStep,
    dragX: Float,
    dragY: Float,
    modifier: Modifier,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (step == GestureOnboardingStep.SwitchTabs) {
            FakeBrowserPage(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                accent = MaterialTheme.colorScheme.tertiary,
            )
        } else {
            MiniTabCard(modifier = Modifier.width(190.dp))
        }
        FakeBrowserPage(
            modifier = Modifier
                .fillMaxSize()
                .offset {
                    if (step == GestureOnboardingStep.SwitchTabs) {
                        IntOffset((dragX * 0.55f).roundToInt(), 0)
                    } else {
                        IntOffset(0, (dragY.coerceAtMost(0f) * 0.58f).roundToInt())
                    }
                },
            accent = MaterialTheme.colorScheme.primary,
            addressBarModifier = modifier,
        )
    }
}

@Composable
private fun CloseTabPractice(dragY: Float, modifier: Modifier) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        MiniTabCard(
            modifier = modifier
                .width(210.dp)
                .offset { IntOffset(0, (dragY.coerceAtMost(0f) * 0.75f).roundToInt()) }
                .graphicsLayer {
                    alpha = (1f - (-dragY.coerceAtMost(0f) / 320f)).coerceIn(0.25f, 1f)
                },
        )
    }
}

@Composable
private fun FakeBrowserPage(
    modifier: Modifier,
    accent: Color,
    showAddressBar: Boolean = true,
    addressBarModifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.58f)
                    .height(18.dp)
                    .background(accent.copy(alpha = 0.8f), RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.height(18.dp))
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (index == 3) 0.64f else 1f)
                        .height(10.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            RoundedCornerShape(8.dp),
                        ),
                )
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.weight(1f))
            if (showAddressBar) FakeAddressBar(addressBarModifier)
        }
    }
}

@Composable
private fun FakeAddressBar(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 6.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "candy://gestures",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MiniTabCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(260.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Candy",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.tertiaryContainer,
                            ),
                        ),
                        RoundedCornerShape(18.dp),
                    ),
            )
        }
    }
}

@Composable
private fun GestureDirectionBadge(
    step: GestureOnboardingStep,
    modifier: Modifier = Modifier,
) {
    val direction = when (step) {
        GestureOnboardingStep.SwitchTabs -> "↔"
        GestureOnboardingStep.OpenTabOverview,
        GestureOnboardingStep.CloseTab,
        -> "↑"
    }
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = gestureAccent(step),
        shadowElevation = 5.dp,
    ) {
        Text(
            text = direction,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp),
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun gestureAccent(step: GestureOnboardingStep): Color = when (step) {
    GestureOnboardingStep.OpenTabOverview -> CandyPink
    GestureOnboardingStep.SwitchTabs,
    GestureOnboardingStep.CloseTab,
    -> CandyPurple
}

@Composable
private fun stepTitle(step: GestureOnboardingStep): String = stringResource(
    when (step) {
        GestureOnboardingStep.SwitchTabs -> R.string.onboarding_switch_tabs_title
        GestureOnboardingStep.OpenTabOverview -> R.string.onboarding_open_tabs_title
        GestureOnboardingStep.CloseTab -> R.string.onboarding_close_tab_title
    },
)

@Composable
private fun stepDescription(step: GestureOnboardingStep): String = stringResource(
    when (step) {
        GestureOnboardingStep.SwitchTabs -> R.string.onboarding_switch_tabs_description
        GestureOnboardingStep.OpenTabOverview -> R.string.onboarding_open_tabs_description
        GestureOnboardingStep.CloseTab -> R.string.onboarding_close_tab_description
    },
)
