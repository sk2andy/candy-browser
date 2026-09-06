package dev.sk2andy.materialbrowser.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TabOverviewModeTest {
    @Test
    fun `wire values round trip`() {
        TabOverviewMode.entries.forEach { mode ->
            assertEquals(mode, TabOverviewMode.fromWireValue(mode.wireValue))
        }
    }

    @Test
    fun `unknown values use the requested fallback`() {
        assertEquals(TabOverviewMode.Hero, TabOverviewMode.fromWireValue(null))
        assertEquals(TabOverviewMode.Hero, TabOverviewMode.fromWireValue("unknown"))
        assertEquals(
            TabOverviewMode.Grid,
            TabOverviewMode.fromWireValue("unknown", fallback = TabOverviewMode.Grid),
        )
    }
}
