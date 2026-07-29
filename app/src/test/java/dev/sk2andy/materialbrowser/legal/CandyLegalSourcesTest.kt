package dev.sk2andy.materialbrowser.legal

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandyLegalSourcesTest {
    @Test
    fun allDestinationsAreUniqueHttpsUrls() {
        val rawDestinations = buildList {
            add(CandyLegalSources.GITHUB_PROFILE_URL)
            CandyLegalSources.thirdPartyNotices.forEach { notice ->
                add(notice.sourceUrl)
                add(notice.licenseUrl)
            }
        }

        assertEquals(rawDestinations.distinct(), CandyLegalSources.destinations)
        rawDestinations.forEach { destination ->
            val uri = URI(destination)
            assertEquals("https", uri.scheme)
            assertTrue(uri.host.isNotBlank())
            assertTrue(uri.userInfo == null)
        }
    }

    @Test
    fun noticesCoverShippedThirdPartyComponents() {
        assertEquals(
            ThirdPartyComponent.entries,
            CandyLegalSources.thirdPartyNotices.map { it.component },
        )
        assertTrue(CandyLegalSources.thirdPartyNotices.all { it.licenseName.isNotBlank() })
    }

    @Test
    fun uassetsDestinationsStayPinnedToBundledRevision() {
        assertTrue(CandyLegalSources.UASSETS_SOURCE_URL.contains(CandyLegalSources.UASSETS_REVISION))
        assertTrue(CandyLegalSources.UASSETS_LICENSE_URL.contains(CandyLegalSources.UASSETS_REVISION))
    }
}
