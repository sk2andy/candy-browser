package dev.sk2andy.materialbrowser.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class CandyPresentationRulesTest {
    @Test
    fun reduceMotionRemovesHeroTravelDuration() {
        assertEquals(0, CandyPresentationRules.animationDuration(420, reduceMotion = true))
    }

    @Test
    fun defaultMotionKeepsExistingDuration() {
        assertEquals(420, CandyPresentationRules.animationDuration(420, reduceMotion = false))
    }

    @Test
    fun invalidDurationCannotProduceNegativeAnimationTime() {
        assertEquals(0, CandyPresentationRules.animationDuration(-1, reduceMotion = false))
    }
}
