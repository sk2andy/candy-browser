package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartPageSearchTransformRulesTest {
    @Test
    fun `bounds interpolate between hero and focused search`() {
        val source = Rect(left = 400f, top = 900f, right = 600f, bottom = 1_100f)
        val target = Rect(left = 32f, top = 1_700f, right = 900f, bottom = 1_800f)

        assertEquals(source, StartPageSearchTransformRules.bounds(source, target, -1f))
        assertEquals(
            Rect(left = 216f, top = 1_300f, right = 750f, bottom = 1_450f),
            StartPageSearchTransformRules.bounds(source, target, 0.5f),
        )
        assertEquals(target, StartPageSearchTransformRules.bounds(source, target, 2f))
    }

    @Test
    fun `shape morph stays bounded at both endpoints`() {
        assertEquals(48f, StartPageSearchTransformRules.cornerRadius(-1f), 0f)
        assertEquals(35f, StartPageSearchTransformRules.cornerRadius(0.5f), 0f)
        assertEquals(22f, StartPageSearchTransformRules.cornerRadius(2f), 0f)
    }

    @Test
    fun `source remains hidden until reverse completes`() {
        assertFalse(StartPageSearchTransformRules.sourceVisible(editing = true, progress = 0f))
        assertFalse(StartPageSearchTransformRules.sourceVisible(editing = false, progress = 0.5f))
        assertTrue(StartPageSearchTransformRules.sourceVisible(editing = false, progress = 0f))
    }

    @Test
    fun `focused content appears only near transform endpoint`() {
        assertEquals(0f, StartPageSearchTransformRules.targetContentAlpha(0.75f), 0f)
        assertEquals(0.5f, StartPageSearchTransformRules.targetContentAlpha(0.88f), 0.001f)
        assertEquals(1f, StartPageSearchTransformRules.targetContentAlpha(1f), 0f)
    }

    @Test
    fun `carrier hands off without endpoint duplication`() {
        assertEquals(1f, StartPageSearchTransformRules.overlayAlpha(editing = true, 0f), 0f)
        assertEquals(0f, StartPageSearchTransformRules.overlayAlpha(editing = true, 1f), 0f)
        assertEquals(1f, StartPageSearchTransformRules.overlayAlpha(editing = false, 1f), 0f)
        assertEquals(0f, StartPageSearchTransformRules.overlayAlpha(editing = false, 0f), 0f)
    }

    @Test
    fun `resting address container returns only near reverse endpoint`() {
        assertEquals(
            0f,
            StartPageSearchTransformRules.targetContainerAlpha(editing = false, progress = 1f),
            0f,
        )
        assertEquals(
            0f,
            StartPageSearchTransformRules.targetContainerAlpha(editing = false, progress = 0.18f),
            0f,
        )
        assertEquals(
            0.5f,
            StartPageSearchTransformRules.targetContainerAlpha(editing = false, progress = 0.09f),
            0.001f,
        )
        assertEquals(
            1f,
            StartPageSearchTransformRules.targetContainerAlpha(editing = false, progress = 0f),
            0f,
        )
    }

    @Test
    fun `focused target bounds freeze throughout reverse`() {
        assertTrue(
            StartPageSearchTransformRules.shouldUpdateTargetBounds(
                editing = true,
                progress = 0.7f,
            ),
        )
        assertFalse(
            StartPageSearchTransformRules.shouldUpdateTargetBounds(
                editing = false,
                progress = 1f,
            ),
        )
        assertTrue(
            StartPageSearchTransformRules.shouldUpdateTargetBounds(
                editing = false,
                progress = 0f,
            ),
        )
    }

    @Test
    fun `invalid or empty measurements cannot start transform`() {
        assertTrue(StartPageSearchTransformRules.isValidBounds(Rect(0f, 0f, 96f, 96f)))
        assertFalse(StartPageSearchTransformRules.isValidBounds(Rect.Zero))
        assertFalse(
            StartPageSearchTransformRules.isValidBounds(
                Rect(Float.NaN, 0f, 96f, 96f),
            ),
        )
    }
}
