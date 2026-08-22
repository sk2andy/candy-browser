package dev.sk2andy.materialbrowser.blocking

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EasyListCosmeticRulesTest {
    @Test
    fun `parser preserves exclusions exceptions and wildcard host scope`() {
        val rules = EasyListCosmeticRules.parse(
            asset(
                "H\texample.com\tmail.example.com\t${encode(".ad")}",
                "H\twww.google.*\t-\t${encode("#tads")}",
                "H\tamazon.*\t-\t${encode(".AdHolder")}",
                "A\tshop.example.com\t-\t${encode(".ad")}",
            ),
        )

        assertEquals(listOf(".ad"), rules.selectors("https://news.example.com/story"))
        assertTrue(rules.selectors("https://mail.example.com/").isEmpty())
        assertTrue(rules.selectors("https://shop.example.com/").isEmpty())
        assertEquals(listOf("#tads"), rules.selectors("https://www.google.de/search?q=test"))
        assertEquals(listOf("#tads"), rules.selectors("https://www.google.fr/search?q=test"))
        assertEquals(listOf("#tads"), rules.selectors("https://www.google.co.kr/search?q=test"))
        assertEquals(listOf("#tads"), rules.selectors("https://www.google.com.sg/search?q=test"))
        assertTrue(rules.selectors("https://www.google.evil.com/search?q=test").isEmpty())
        assertTrue(rules.selectors("https://www.google.com.de/search?q=test").isEmpty())
        assertEquals(listOf(".AdHolder"), rules.selectors("https://www.amazon.de/s?k=laptop"))
        assertEquals(listOf(".AdHolder"), rules.selectors("https://amazon.co.jp/s?k=laptop"))
        assertTrue(rules.selectors("https://amazon.evil.com/s?k=laptop").isEmpty())
        assertTrue(rules.selectors("https://amazon.com.de/s?k=laptop").isEmpty())
        assertTrue(rules.selectors("https://mail.google.com/").isEmpty())
        assertTrue(rules.selectors("https://maps.google.com/").isEmpty())
        assertTrue(rules.selectors("https://accounts.google.com/").isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parser rejects unsafe selectors`() {
        EasyListCosmeticRules.parse(
            asset("H\texample.com\t-\t${encode("div{display:block}")}"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `malformed asset fails closed`() {
        EasyListCosmeticRules.parse("not-an-asset")
    }

    @Test
    fun `merge deduplicates records and applies exceptions across sources`() {
        val easyList = EasyListCosmeticRules.parse(
            asset(
                "H\tfuqer.com\t-\t${encode(".spot-thumbs > .right")}",
                "H\texample.com\t-\t${encode(".duplicate")}",
            ),
        )
        val uAssets = EasyListCosmeticRules.parse(
            asset(
                "A\tfuqer.com\t-\t${encode(".spot-thumbs > .right")}",
                "H\texample.com\t-\t${encode(".duplicate")}",
                "H\texample.com\t-\t${encode(".additional")}",
                header = EasyListCosmeticRules.UASSETS_HEADER,
            ),
            EasyListCosmeticRules.UASSETS_HEADER,
        )

        val merged = EasyListCosmeticRules.merge(easyList, uAssets)

        assertTrue(merged.selectors("https://fuqer.com/").isEmpty())
        assertEquals(
            listOf(".additional", ".duplicate"),
            merged.selectors("https://example.com/"),
        )
        assertEquals(4, merged.size)
    }

    @Test
    fun `generic rules respect selector exceptions and generic hide exceptions`() {
        val rules = EasyListCosmeticRules.parse(
            asset(
                "H\t*\t-\t${encode(".generic-ad")}",
                "H\texample.com\t-\t${encode(".specific-ad")}",
                "A\tnews.example.com\t-\t${encode(".generic-ad")}",
                "D\tshop.example.com\t-\t-",
                "D\tstream.*\tsafe.stream.de\t-",
                header = EasyListCosmeticRules.UASSETS_HEADER,
            ),
            EasyListCosmeticRules.UASSETS_HEADER,
        )

        assertEquals(
            listOf(".generic-ad", ".specific-ad"),
            rules.selectors("https://example.com/"),
        )
        assertEquals(
            listOf(".specific-ad"),
            rules.selectors("https://news.example.com/"),
        )
        assertEquals(
            listOf(".specific-ad"),
            rules.selectors("https://shop.example.com/"),
        )
        assertTrue(rules.selectors("https://video.stream.de/").isEmpty())
        assertEquals(
            listOf(".generic-ad"),
            rules.selectors("https://safe.stream.de/"),
        )
    }

    @Test
    fun `easylist v2 resolves generic policy without entering scoped payload`() {
        val rules = EasyListCosmeticRules.parse(
            asset(
                "H\t*\t-\t${encode(".generic-ad")}",
                "H\t*\tnews.example.com\t${encode("#conditional-ad")}",
                "H\texample.com\t-\t${encode(".scoped-ad")}",
                "A\tnews.example.com\t-\t${encode(".generic-ad")}",
                header = EasyListCosmeticRules.EASYLIST_V2_HEADER,
            ),
            EasyListCosmeticRules.EASYLIST_V2_HEADER,
        )

        assertEquals(listOf(".scoped-ad"), rules.scopedSelectors("https://news.example.com/"))
        assertEquals(
            GenericCosmeticPolicy(
                disabled = false,
                deniedSelectors = listOf("#conditional-ad", ".generic-ad"),
            ),
            rules.genericPolicy("https://news.example.com/"),
        )
        assertEquals(
            GenericCosmeticPolicy(disabled = false),
            rules.genericPolicy("https://shop.example.com/"),
        )
        assertEquals(
            GenericCosmeticPolicy(disabled = false),
            rules.genericPolicyForHost("SHOP.EXAMPLE.COM."),
        )
        assertEquals(
            GenericCosmeticPolicy(disabled = true),
            rules.genericPolicyForHost("not a host"),
        )
    }

    @Test
    fun `generic hide exception disables generics but preserves scoped selectors`() {
        val rules = EasyListCosmeticRules.parse(
            asset(
                "H\t*\t-\t${encode(".generic-ad")}",
                "H\texample.com\t-\t${encode(".scoped-ad")}",
                "D\tnews.example.com\t-\t-",
                header = EasyListCosmeticRules.EASYLIST_V2_HEADER,
            ),
            EasyListCosmeticRules.EASYLIST_V2_HEADER,
        )

        assertEquals(listOf(".scoped-ad"), rules.scopedSelectors("https://news.example.com/"))
        assertEquals(
            GenericCosmeticPolicy(disabled = true),
            rules.genericPolicy("https://news.example.com/"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parser rejects wrong source header`() {
        EasyListCosmeticRules.parse(
            asset(header = EasyListCosmeticRules.UASSETS_HEADER),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `easylist v1 parser rejects generic grammar`() {
        EasyListCosmeticRules.parse(
            asset("H\t*\t-\t${encode(".generic-ad")}"),
        )
    }

    private fun asset(
        vararg lines: String,
        header: String = EasyListCosmeticRules.HEADER,
    ): String =
        (
            listOf(
                header,
                "# Hide rules: ${lines.count { it.startsWith("H\t") }}",
                "# Exception rules: ${lines.count { it.startsWith("A\t") }}",
                "# Generic hide exceptions: ${lines.count { it.startsWith("D\t") }}",
            ) + lines
        ).joinToString("\n")

    private fun encode(selector: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(selector.toByteArray(Charsets.UTF_8))
}
