package dev.sk2andy.materialbrowser.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderChromeRulesTest {
    @Test
    fun `eye repeatedly switches between expanded and compact controls`() {
        var mode = ReaderChromeMode.Expanded
        repeat(12) {
            mode = ReaderChromeRules.toggle(mode)
            assertEquals(if (it % 2 == 0) ReaderChromeMode.Compact else ReaderChromeMode.Expanded, mode)
        }
    }

    @Test
    fun `back restores hidden controls to compact without exiting`() {
        assertEquals(ReaderChromeMode.Compact, ReaderChromeRules.restore(ReaderChromeMode.Hidden))
        assertEquals(ReaderChromeMode.Compact, ReaderChromeRules.restore(ReaderChromeMode.Compact))
        assertEquals(ReaderChromeMode.Expanded, ReaderChromeRules.restore(ReaderChromeMode.Expanded))
    }
}
