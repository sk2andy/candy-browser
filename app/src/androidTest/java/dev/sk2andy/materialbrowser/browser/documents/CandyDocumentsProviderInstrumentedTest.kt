package dev.sk2andy.materialbrowser.browser.documents

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.ProviderInfo
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.CandyDocumentsProvider
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.ProfileLockTrigger
import dev.sk2andy.materialbrowser.browser.ProfileProtection
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.DownloadEntryIds
import dev.sk2andy.materialbrowser.data.DownloadProvenanceStore
import dev.sk2andy.materialbrowser.reader.ReaderBlock
import dev.sk2andy.materialbrowser.reader.ReaderBlockKind
import dev.sk2andy.materialbrowser.reader.ReaderDocument
import dev.sk2andy.materialbrowser.reader.ReaderLibraryStore
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CandyDocumentsProviderInstrumentedTest {
    private val baseContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val testId = UUID.randomUUID().toString()
    private val directory = File(baseContext.cacheDir, "documents-$testId").apply { mkdirs() }
    private val context = object : ContextWrapper(baseContext) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = directory
        override fun getCacheDir(): File = File(directory, "cache").apply { mkdirs() }
        override fun getNoBackupFilesDir(): File = File(directory, "no-backup").apply { mkdirs() }
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            baseContext.getSharedPreferences("documents-$testId-$name", mode)
    }
    private val profiles = BrowserSessionStore(context)
    private val reader = ReaderLibraryStore(context)
    private val profile = BrowserProfile("documents-profile", "🍬")
    private val provider = CandyDocumentsProvider()
    private val downloads = mutableListOf<Uri>()

    @Before
    fun setUp() {
        profiles.saveProfiles(listOf(profile), profile.id)
        provider.attachInfo(context, ProviderInfo().apply {
            authority = "${context.packageName}.documents-test"
            exported = true
            grantUriPermissions = true
            readPermission = "android.permission.MANAGE_DOCUMENTS"
            writePermission = "android.permission.MANAGE_DOCUMENTS"
        })
    }

    @After
    fun tearDown() {
        downloads.forEach { context.contentResolver.delete(it, null, null) }
        directory.deleteRecursively()
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun savedRegularArticlesOpenAsSeekablePdfAndOldGrantFailsAfterProtection() {
        val saved = requireNotNull(reader.saveSnapshot(document("Saved article"), 0f, false, sourceProfileId = profile.id))
        reader.saveSnapshot(document("Legacy article"), 0f, false)
        reader.saveSnapshot(document("Missing profile"), 0f, false, sourceProfileId = "missing")
        val id = CandyDocumentRules.articleId(saved.id)
        provider.queryChildDocuments(CandyDocumentRules.READER, null, sortOrder = null).use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals(id, cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)))
            assertEquals("application/pdf", cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow(Document.COLUMN_FLAGS)))
        }
        val renderer = PdfRenderer(provider.openDocument(id, "r", null))
        try {
            assertTrue(renderer.pageCount >= 1)
        } finally {
            renderer.close()
        }
        assertTrue(context.cacheDir.listFiles().orEmpty().isEmpty())
        assertReadRejected(id, "rw")
        profiles.saveProfiles(listOf(profile.copy(protection = ProfileProtection(ProfileLockTrigger.AppClosed))), profile.id)
        assertReadRejected(id, "r")
        provider.queryChildDocuments(CandyDocumentRules.READER, null, sortOrder = null).use { assertEquals(0, it.count) }
    }

    @Test
    fun completedDownloadsRequireKnownRegularProfileAndExistingLocalFile() {
        val regular = createDownload("regular-$testId.txt", isPrivate = false)
        createDownload("private-$testId.txt", isPrivate = true)
        createDownload("legacy-$testId.txt", isPrivate = false, known = false)
        createDownload("pending-$testId.txt", isPrivate = false, pending = true)
        val documentId = CandyDocumentRules.downloadId(DownloadEntryIds.encodeMediaStoreId(ContentUris.parseId(regular)))
        provider.queryChildDocuments(CandyDocumentRules.DOWNLOADS, null, sortOrder = null).use { assertEquals(1, it.count) }
        ParcelFileDescriptor.AutoCloseInputStream(provider.openDocument(documentId, "r", null)).use {
            assertEquals("Candy document fixture", it.bufferedReader().readText())
        }
        DownloadProvenanceStore(context).record(
            DownloadEntryIds.encodeMediaStoreId(ContentUris.parseId(regular)),
            "regular-$testId.txt",
            profile.id,
            isPrivate = true,
        )
        assertReadRejected(documentId, "r")
        context.contentResolver.delete(regular, null, null)
        assertReadRejected(documentId, "r")
        provider.queryChildDocuments(CandyDocumentRules.DOWNLOADS, null, sortOrder = null).use { assertEquals(0, it.count) }
    }

    @Test
    fun unknownDocumentPathsAndWriteModesAreRejected() {
        assertReadRejected("reader:../reader_library_v1.json", "r")
        assertReadRejected("download:../../123", "r")
        assertReadRejected(CandyDocumentRules.ROOT, "w")
        assertFalse(provider.isChildDocument(CandyDocumentRules.READER, "download:123"))
        assertTrue(provider.isChildDocument(CandyDocumentRules.ROOT, CandyDocumentRules.READER))
    }

    private fun createDownload(name: String, isPrivate: Boolean, known: Boolean = true, pending: Boolean = false): Uri {
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }))
        downloads += uri
        context.contentResolver.openOutputStream(uri)?.use { it.write("Candy document fixture".toByteArray()) }
        if (!pending) context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        if (known) DownloadProvenanceStore(context).record(
            DownloadEntryIds.encodeMediaStoreId(ContentUris.parseId(uri)), name, profile.id, isPrivate,
        )
        return uri
    }

    private fun assertReadRejected(id: String, mode: String) {
        try {
            provider.openDocument(id, mode, null).close()
            fail("Unavailable or writable document must be rejected")
        } catch (_: FileNotFoundException) {
            // Expected: no fallback path or writable descriptor is exposed.
        }
    }

    private fun document(title: String) = ReaderDocument(
        title = title,
        sourceUrl = "https://example.com/${title.replace(' ', '-')}",
        siteName = "Example",
        blocks = listOf(ReaderBlock(ReaderBlockKind.Paragraph, "Saved offline article text. ".repeat(8))),
    )
}
