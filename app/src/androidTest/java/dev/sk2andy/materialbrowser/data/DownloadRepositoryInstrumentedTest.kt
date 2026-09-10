package dev.sk2andy.materialbrowser.data

import android.app.DownloadManager
import android.content.ContentValues
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadRepositoryInstrumentedTest {
    private lateinit var context: Context
    private lateinit var resolver: android.content.ContentResolver
    private lateinit var repository: DownloadRepository
    private val mediaUris = mutableListOf<Uri>()
    private val managerIds = mutableListOf<Long>()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        resolver = context.contentResolver
        repository = DownloadRepository(context)
    }

    @After
    fun cleanFixtures() {
        DownloadRuntimeRegistry.completed(42)
        mediaUris.forEach { uri -> runCatching { resolver.delete(uri, null, null) } }
        if (managerIds.isNotEmpty()) {
            context.getSystemService(DownloadManager::class.java)
                .remove(*managerIds.toLongArray())
        }
    }

    @Test
    fun readsAndClearsCompletedGeckoMediaStoreDownload() {
        val name = "candy-gecko-${UUID.randomUUID()}.txt"
        val uri = requireNotNull(
            resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                },
            ),
        )
        mediaUris += uri
        resolver.openOutputStream(uri, "w")!!.use { output ->
            output.write("Candy download".encodeToByteArray())
        }

        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
            null,
            null,
        )
        repository = DownloadRepository(context, ownerPackageName = ownerPackage(uri))
        val completed = repository.snapshot().single { entry -> entry.name == name }
        assertEquals(DownloadStatus.Successful, completed.status)
        assertNotNull(repository.contentUri(completed))
        assertTrue(repository.clear(listOf(completed)))
        assertFalse(repository.snapshot().any { entry -> entry.name == name })
        mediaUris.remove(uri)
    }

    @Test
    fun readsAndroidDownloadManagerEntryWithSourceAndNamespacedId() {
        val name = "candy-system-${UUID.randomUUID()}.bin"
        val source = "https://downloads.example.invalid/$name"
        val manager = context.getSystemService(DownloadManager::class.java)
        val id = manager.enqueue(
            DownloadManager.Request(Uri.parse(source))
                .setTitle(name)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name),
        )
        managerIds += id

        val entry = repository.snapshot().single { candidate -> candidate.id == id }
        assertEquals(name, entry.name)
        assertEquals(source, entry.source)
        assertTrue(entry.id >= 0L)
    }

    @Test
    fun readsLiveGeckoProgressFromProcessRegistry() {
        DownloadRuntimeRegistry.started(
            id = 42,
            name = "active-gecko.bin",
            source = "https://downloads.example/active-gecko.bin",
            mime = "application/octet-stream",
            total = 1_000L,
            startedAt = 100L,
        )
        DownloadRuntimeRegistry.progress(id = 42, bytes = 400L, total = 1_000L, updatedAt = 200L)

        val active = repository.snapshot().single { entry -> entry.name == "active-gecko.bin" }
        assertEquals(DownloadStatus.Running, active.status)
        assertEquals(400L, active.bytes)
        assertEquals(1_000L, active.total)

        DownloadRuntimeRegistry.completed(42)
    }

    @Test
    fun mergesLiveRegistryProgressWithItsPendingMediaStoreRow() {
        val name = "active-gecko-${UUID.randomUUID()}.bin"
        val uri = requireNotNull(
            resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                },
            ),
        )
        mediaUris += uri
        val mediaStoreId = ContentUris.parseId(uri)
        repository = DownloadRepository(context, ownerPackageName = ownerPackage(uri))
        DownloadRuntimeRegistry.started(
            id = 42,
            name = name,
            source = "https://downloads.example/$name",
            mime = "application/octet-stream",
            total = 1_000L,
            startedAt = System.currentTimeMillis(),
            mediaStoreId = mediaStoreId,
        )
        DownloadRuntimeRegistry.progress(
            id = 42,
            bytes = 400L,
            total = 1_000L,
            updatedAt = System.currentTimeMillis(),
        )

        val matches = repository.snapshot().filter { entry -> entry.name == name }
        assertEquals(1, matches.size)
        assertEquals(DownloadStatus.Running, matches.single().status)
        assertEquals(400L, matches.single().bytes)

        DownloadRuntimeRegistry.failed(42, cancelled = true, updatedAt = System.currentTimeMillis())
        assertTrue(repository.clear(DownloadRuntimeRegistry.snapshot()))
        assertFalse(repository.snapshot().any { entry -> entry.name == name })
        mediaUris.remove(uri)
    }

    private fun ownerPackage(uri: Uri): String = requireNotNull(
        resolver.query(
            uri,
            arrayOf(MediaStore.Downloads.OWNER_PACKAGE_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            cursor.moveToFirst()
            cursor.getString(0)
        },
    )
}
