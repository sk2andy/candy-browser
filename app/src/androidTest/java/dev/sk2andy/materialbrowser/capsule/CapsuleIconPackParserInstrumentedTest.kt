package dev.sk2andy.materialbrowser.capsule

import android.util.Xml
import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapsuleIconPackParserInstrumentedTest {
    @Test
    fun parserDeduplicatesDrawablesAndKeepsComponentSearchTerms() {
        val parser = Xml.newPullParser().apply {
            setInput(
                StringReader(
                    """
                    <resources>
                        <item component="ComponentInfo{com.alpha/.MainActivity}" drawable="alpha_mail" />
                        <item component="ComponentInfo{com.beta/.InboxActivity}" drawable="alpha_mail" />
                        <item component="ComponentInfo{com.bad/.Main}" drawable="../invalid" />
                        <item drawable="standalone_icon" />
                    </resources>
                    """.trimIndent(),
                ),
            )
        }

        val entries = CapsuleIconPackParser.parse("icons.pack", parser)

        assertEquals(listOf("alpha_mail", "standalone_icon"), entries.map { it.drawableName })
        assertEquals(
            listOf(entries.first()),
            CapsuleIconPackRules.visibleEntries(entries, "beta inbox"),
        )
        assertTrue(entries.all { it.packageName == "icons.pack" })
    }
}
