package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteAnimationSpeedTest {
    @Test
    fun `normal speed is faster than the relaxed legacy timing`() {
        assertEquals(FavoriteAnimationSpeed.Normal, FavoriteAnimationSpeed.Default)
        assertTrue(
            FavoriteAnimationSpeed.Normal.morphDurationMillis <
                FavoriteAnimationSpeed.Relaxed.morphDurationMillis,
        )
        assertTrue(
            FavoriteAnimationSpeed.Normal.morphPauseMillis <
                FavoriteAnimationSpeed.Relaxed.morphPauseMillis,
        )
    }

    @Test
    fun `stored speed falls back safely`() {
        assertEquals(
            FavoriteAnimationSpeed.Fast,
            FavoriteAnimationSpeed.fromWireValue("fast"),
        )
        assertEquals(
            FavoriteAnimationSpeed.Default,
            FavoriteAnimationSpeed.fromWireValue("future-speed"),
        )
        assertEquals(
            FavoriteAnimationSpeed.Default,
            FavoriteAnimationSpeed.fromWireValue(null),
        )
    }
}
