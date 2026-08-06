package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewTabCreationMotionRulesTest {
    private val source = Rect(left = 10f, top = 20f, right = 50f, bottom = 60f)
    private val destination = Rect(left = 100f, top = 200f, right = 220f, bottom = 360f)

    @Test
    fun `seed starts at source and settles at destination`() {
        val start = NewTabCreationMotionRules.projection(source, destination, 14f, 0f)
        val end = NewTabCreationMotionRules.projection(source, destination, 14f, 1f)

        assertEquals(source.center, start.center)
        assertEquals(14f, start.width, 0.001f)
        assertEquals(14f, start.height, 0.001f)
        assertEquals(destination.center, end.center)
        assertEquals(destination.width, end.width, 0.001f)
        assertEquals(destination.height, end.height, 0.001f)
        assertEquals(1f, end.coreScale, 0.001f)
        assertEquals(0f, end.alpha, 0.001f)
    }

    @Test
    fun `missing source unfolds in place with shorter timing`() {
        val start = NewTabCreationMotionRules.projection(null, destination, 14f, 0f)
        val middle = NewTabCreationMotionRules.projection(null, destination, 14f, 0.5f)

        assertEquals(destination.center, start.center)
        assertEquals(destination.center, middle.center)
        assertEquals(
            NewTabCreationMotionRules.FALLBACK_DURATION_MILLIS,
            NewTabCreationMotionRules.durationMillis(hasSourceBounds = false),
        )
        assertEquals(
            NewTabCreationMotionRules.DURATION_MILLIS,
            NewTabCreationMotionRules.durationMillis(hasSourceBounds = true),
        )
    }

    @Test
    fun `projection clamps progress and remains bounded`() {
        val before = NewTabCreationMotionRules.projection(source, destination, 14f, -2f)
        val after = NewTabCreationMotionRules.projection(source, destination, 14f, 4f)

        assertEquals(source.center, before.center)
        assertEquals(destination.center, after.center)
        assertTrue(before.width in 14f..destination.width)
        assertTrue(after.width in 14f..destination.width)
        assertTrue(before.height in 14f..destination.height)
        assertTrue(after.height in 14f..destination.height)
        assertTrue(before.alpha in 0f..1f)
        assertTrue(after.alpha in 0f..1f)
        assertTrue(before.coreScale in 0.88f..1f)
        assertTrue(after.coreScale in 0.88f..1f)
        assertTrue(before.cornerFraction in 0.28f..0.5f)
        assertTrue(after.cornerFraction in 0.28f..0.5f)
    }

    @Test
    fun `travel and unfold progress monotonically toward destination`() {
        val frames = (0..10).map { step ->
            NewTabCreationMotionRules.projection(
                sourceBounds = source,
                destinationBounds = destination,
                seedSize = 14f,
                progress = step / 10f,
            )
        }

        assertTrue(frames.zipWithNext().all { (first, second) -> second.center.x >= first.center.x })
        assertTrue(frames.zipWithNext().all { (first, second) -> second.center.y >= first.center.y })
        assertTrue(frames.zipWithNext().all { (first, second) -> second.width >= first.width })
        assertTrue(frames.zipWithNext().all { (first, second) -> second.height >= first.height })
    }

    @Test
    fun `invalid bounds use destination fallback`() {
        val invalid = Rect(Offset(Float.NaN, 0f), Offset(20f, 20f))
        val frame = NewTabCreationMotionRules.projection(invalid, destination, 14f, 0f)

        assertFalse(NewTabCreationMotionRules.hasUsableBounds(invalid))
        assertTrue(NewTabCreationMotionRules.hasUsableBounds(source))
        assertEquals(destination.center, frame.center)
    }

    @Test
    fun `stale completion cannot clear newer request`() {
        val controller = NewTabCreationMotionController()
        controller.launch(
            sourceBounds = source,
            destinationBounds = null,
            isIncognito = false,
        )
        val firstRequestId = requireNotNull(controller.request).id
        controller.launch(
            sourceBounds = destination,
            destinationBounds = null,
            isIncognito = true,
        )
        val secondRequest = requireNotNull(controller.request)

        assertNotEquals(firstRequestId, secondRequest.id)
        controller.updateDestination(destination)
        assertEquals(destination, controller.request?.destinationBounds)
        controller.complete(firstRequestId)
        assertEquals(secondRequest.id, controller.request?.id)
        controller.complete(secondRequest.id)
        assertNull(controller.request)
    }
}
