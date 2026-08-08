package dev.sk2andy.materialbrowser.ui

import android.view.MotionEvent
import android.view.View
import kotlin.math.absoluteValue

internal enum class LinkPeekGesturePhase {
    Idle,
    Tracking,
    Armed,
    Cancelled,
    Completed,
}

internal data class LinkPeekGestureUpdate(
    val phase: LinkPeekGesturePhase,
    val progress: Float,
    val shouldOpen: Boolean = false,
    val shouldDismiss: Boolean = false,
    val emitThresholdHaptic: Boolean = false,
)

/** Pure, single-use state machine for the held WebView long-press gesture. */
internal class LinkPeekGestureMachine(
    private val threshold: Float,
    private val touchSlop: Float,
) {
    var phase: LinkPeekGesturePhase = LinkPeekGesturePhase.Idle
        private set

    fun reset() {
        phase = LinkPeekGesturePhase.Idle
    }

    fun begin(): LinkPeekGestureUpdate {
        if (phase != LinkPeekGesturePhase.Idle) return current()
        phase = LinkPeekGesturePhase.Tracking
        return current()
    }

    fun move(dragX: Float, dragY: Float): LinkPeekGestureUpdate {
        if (phase == LinkPeekGesturePhase.Idle) begin()
        if (phase == LinkPeekGesturePhase.Cancelled || phase == LinkPeekGesturePhase.Completed) {
            return current()
        }
        if (phase != LinkPeekGesturePhase.Armed &&
            maxOf(dragX.absoluteValue, dragY.absoluteValue) < touchSlop
        ) {
            return current()
        }

        val progress = if (dragY > 0f && dragY.absoluteValue > dragX.absoluteValue) {
            progress(dragY)
        } else {
            0f
        }
        val wasArmed = phase == LinkPeekGesturePhase.Armed
        phase = if (progress >= 1f) LinkPeekGesturePhase.Armed else LinkPeekGesturePhase.Tracking
        return current(
            progress = progress,
            emitThresholdHaptic = phase == LinkPeekGesturePhase.Armed && !wasArmed,
        )
    }

    fun release(): LinkPeekGestureUpdate = when (phase) {
        LinkPeekGesturePhase.Armed -> {
            phase = LinkPeekGesturePhase.Completed
            current(progress = 1f, shouldOpen = true)
        }

        LinkPeekGesturePhase.Completed -> current(progress = 1f)
        else -> {
            phase = LinkPeekGesturePhase.Cancelled
            current(shouldDismiss = true)
        }
    }

    fun cancel(): LinkPeekGestureUpdate {
        if (phase != LinkPeekGesturePhase.Completed) phase = LinkPeekGesturePhase.Cancelled
        return current(shouldDismiss = phase == LinkPeekGesturePhase.Cancelled)
    }

    private fun progress(dragY: Float): Float {
        if (threshold <= 0f) return 0f
        return (dragY / threshold).coerceIn(0f, 1f)
    }

    private fun current(
        progress: Float = if (phase == LinkPeekGesturePhase.Armed ||
            phase == LinkPeekGesturePhase.Completed
        ) {
            1f
        } else {
            0f
        },
        shouldOpen: Boolean = false,
        shouldDismiss: Boolean = false,
        emitThresholdHaptic: Boolean = false,
    ) = LinkPeekGestureUpdate(
        phase = phase,
        progress = progress,
        shouldOpen = shouldOpen,
        shouldDismiss = shouldDismiss,
        emitThresholdHaptic = emitThresholdHaptic,
    )
}

/**
 * Observes WebView events until Link Peek appears. Peek then owns that held pointer stream and
 * cancels Chromium plus pull-to-refresh so the page cannot scroll behind the preview.
 */
internal class LinkPeekTouchListener(
    private val threshold: (View) -> Float,
    private val touchSlop: Float,
    private val delegate: View.OnTouchListener,
    private val isVisible: () -> Boolean,
    private val onProgress: (Float, Boolean) -> Unit,
    private val onOpen: () -> Unit,
    private val onDismiss: () -> Unit,
    private val onThresholdHaptic: (View) -> Unit,
    private val onPointerDown: () -> Unit,
    private val onPointerEnd: () -> Unit,
) : View.OnTouchListener {
    private var machine = LinkPeekGestureMachine(threshold = 1f, touchSlop = touchSlop)
    private var downX = 0f
    private var downY = 0f
    private var ownsStream = false

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                machine = LinkPeekGestureMachine(
                    threshold = threshold(view).coerceAtLeast(1f),
                    touchSlop = touchSlop,
                )
                ownsStream = false
                downX = event.x
                downY = event.y
                onPointerDown()
            }

            MotionEvent.ACTION_MOVE -> if (isVisible()) {
                takeStream(view, event)
                apply(
                    view,
                    machine.move(
                        dragX = event.x - downX,
                        dragY = event.y - downY,
                    ),
                )
            }

            MotionEvent.ACTION_UP -> {
                if (isVisible()) {
                    takeStream(view, event)
                    apply(view, machine.release())
                }
                onPointerEnd()
            }

            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_POINTER_DOWN,
            -> {
                if (isVisible()) {
                    takeStream(view, event)
                    apply(view, machine.cancel())
                }
                onPointerEnd()
            }
        }
        if (!ownsStream) delegate.onTouch(view, event)
        return ownsStream
    }

    private fun takeStream(view: View, event: MotionEvent) {
        if (ownsStream) return
        ownsStream = true
        MotionEvent.obtain(event).also { cancelEvent ->
            cancelEvent.action = MotionEvent.ACTION_CANCEL
            delegate.onTouch(view, cancelEvent)
            view.onTouchEvent(cancelEvent)
            cancelEvent.recycle()
        }
    }

    private fun apply(view: View, update: LinkPeekGestureUpdate) {
        onProgress(update.progress, update.phase == LinkPeekGesturePhase.Armed)
        if (update.emitThresholdHaptic) onThresholdHaptic(view)
        when {
            update.shouldOpen -> onOpen()
            update.shouldDismiss -> onDismiss()
        }
    }
}
