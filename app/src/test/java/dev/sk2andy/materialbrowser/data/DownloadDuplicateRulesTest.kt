package dev.sk2andy.materialbrowser.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadDuplicateRulesTest {
    @Test
    fun `matches successful same-name downloads from same source`() {
        val match = entry(id = 1L, name = "report.pdf", source = REQUEST_URL)

        assertEquals(
            listOf(match),
            DownloadDuplicateRules.candidates(listOf(match), request()),
        )
    }

    @Test
    fun `matches media store download when source is unavailable`() {
        val match = entry(id = -1L, name = "report.pdf", source = "")

        assertEquals(
            listOf(match),
            DownloadDuplicateRules.candidates(listOf(match), request()),
        )
    }

    @Test
    fun `rejects different source and different known profile`() {
        val otherSource = entry(id = 1L, name = "report.pdf", source = "https://example.com/other.pdf")
        val otherProfile = entry(
            id = 2L,
            name = "report.pdf",
            source = REQUEST_URL,
            sourceProfileId = "profile-b",
        )

        assertEquals(emptyList<DownloadEntry>(), DownloadDuplicateRules.candidates(listOf(otherSource, otherProfile), request()))
    }

    @Test
    fun `ignores incomplete downloads and different names`() {
        val pending = entry(id = 1L, status = DownloadStatus.Running)
        val failed = entry(id = 2L, status = DownloadStatus.Failed)
        val differentName = entry(id = 3L, name = "other.pdf")

        assertEquals(
            emptyList<DownloadEntry>(),
            DownloadDuplicateRules.candidates(listOf(pending, failed, differentName), request()),
        )
    }

    @Test
    fun `returns newest duplicate first`() {
        val older = entry(id = 1L, lastModified = 100L)
        val newer = entry(id = 2L, lastModified = 200L)

        assertEquals(
            listOf(newer, older),
            DownloadDuplicateRules.candidates(listOf(older, newer), request()),
        )
    }

    private fun request(profileId: String? = "profile-a") = BrowserDownloadRequest(
        url = REQUEST_URL,
        fileName = "report.pdf",
        mimeType = "application/pdf",
        sourceProfileId = profileId,
    )

    private fun entry(
        id: Long,
        name: String = "report.pdf",
        source: String = REQUEST_URL,
        status: DownloadStatus = DownloadStatus.Successful,
        lastModified: Long = 100L,
        sourceProfileId: String? = null,
    ) = DownloadEntry(
        id = id,
        name = name,
        source = source,
        status = status,
        bytes = 1L,
        total = 1L,
        lastModified = lastModified,
        mime = "application/pdf",
        sourceProfileId = sourceProfileId,
    )

    private companion object {
        const val REQUEST_URL = "https://example.com/report.pdf"
    }
}
