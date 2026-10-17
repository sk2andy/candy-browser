package dev.sk2andy.materialbrowser.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownloadFileNameRulesTest {
    @Test
    fun `path separators controls and format characters cannot escape chosen directory`() {
        assertEquals("_report_.pdf", DownloadFileNameRules.fileName("../report\n.pdf", "application/pdf"))
        assertEquals("report_name.txt", DownloadFileNameRules.fileName("report\u202ename", "text/plain"))
        assertEquals("a_b_c_d_e_f_g_h_i_j.txt", DownloadFileNameRules.fileName("a/b\\c:d*e?f\"g<h>i|j", "text/plain"))
    }

    @Test
    fun `empty names use safe fallback with inferred extension`() {
        assertEquals("download.pdf", DownloadFileNameRules.fileName("...", "application/pdf"))
        assertEquals("download", DownloadFileNameRules.fileName("  ", "application/octet-stream"))
        assertEquals("photo.jpg", DownloadFileNameRules.fileName(" photo ", "image/jpeg"))
        assertEquals("installer.apk", DownloadFileNameRules.fileName("installer", DownloadFileNameRules.ANDROID_PACKAGE_MIME_TYPE))
    }

    @Test
    fun `existing extensions and Unicode user names retain Android behavior`() {
        assertEquals("report.PDF", DownloadFileNameRules.fileName("report.PDF", "text/plain"))
        assertEquals("日本語.txt", DownloadFileNameRules.fileName("日本語", "text/plain"))
        assertEquals("archive.tar.gz", DownloadFileNameRules.fileName("archive.tar.gz", "application/octet-stream"))
    }

    @Test
    fun `long names stay bounded and preserve short suffix`() {
        val result = DownloadFileNameRules.fileName("x".repeat(200) + ".pdf", "application/pdf")
        assertEquals(120, result.length)
        assertTrue(result.endsWith(".pdf"))
        assertEquals(120, DownloadFileNameRules.fileName("x".repeat(200), "application/octet-stream").length)
    }
}
