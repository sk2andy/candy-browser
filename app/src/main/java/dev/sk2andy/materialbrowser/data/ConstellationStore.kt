package dev.sk2andy.materialbrowser.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.sk2andy.materialbrowser.constellations.ConstellationDocument
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** All queries require an explicit profile. The caller serializes I/O outside the main thread. */
internal class ConstellationStore(context: Context) {
    private val databaseFile = File(context.noBackupFilesDir, DATABASE_NAME)
    private val helper = DatabaseHelper(context.applicationContext, databaseFile.absolutePath)

    @Synchronized
    fun enabledProfiles(): Set<String> = helper.readableDatabase.rawQuery(
        "SELECT profile_id FROM profiles",
        null,
    ).use { cursor ->
        buildSet {
            while (cursor.moveToNext()) add(cursor.getString(0))
        }
    }

    @Synchronized
    fun setEnabled(profileId: String, enabled: Boolean) {
        require(validProfile(profileId))
        val database = helper.writableDatabase
        database.beginTransaction()
        try {
            if (enabled) {
                database.insertWithOnConflict(
                    "profiles",
                    null,
                    ContentValues().apply { put("profile_id", profileId) },
                    SQLiteDatabase.CONFLICT_IGNORE,
                )
            } else {
                database.delete("documents", "profile_id = ?", arrayOf(profileId))
                database.delete("profiles", "profile_id = ?", arrayOf(profileId))
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    @Synchronized
    fun documents(profileId: String): List<ConstellationDocument> {
        if (!validProfile(profileId)) return emptyList()
        return helper.readableDatabase.rawQuery(
            "SELECT url, title, excerpt, visited_at, embedding FROM documents " +
                "WHERE profile_id = ? ORDER BY visited_at DESC, url LIMIT ?",
            arrayOf(profileId, MAX_DOCUMENTS_PER_PROFILE.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val vector = decodeEmbedding(cursor.getBlob(4)) ?: continue
                    val url = CanonicalWebUrl.key(cursor.getString(0)) ?: continue
                    add(
                        ConstellationDocument(
                            profileId = profileId,
                            url = url,
                            title = cursor.getString(1).take(MAX_TITLE_CHARS),
                            excerpt = cursor.getString(2).take(MAX_EXCERPT_CHARS),
                            visitedAt = cursor.getLong(3),
                            embedding = vector,
                        ),
                    )
                }
            }
        }
    }

    @Synchronized
    fun hasIndexedVisit(profileId: String, url: String, visitedAt: Long): Boolean {
        if (!validProfile(profileId)) return false
        val canonicalUrl = CanonicalWebUrl.key(url) ?: return false
        return helper.readableDatabase.rawQuery(
            "SELECT 1 FROM documents WHERE profile_id = ? AND url = ? AND visited_at >= ? LIMIT 1",
            arrayOf(profileId, canonicalUrl, visitedAt.toString()),
        ).use { it.moveToFirst() }
    }

    @Synchronized
    fun upsert(document: ConstellationDocument) {
        require(validProfile(document.profileId))
        val url = CanonicalWebUrl.key(document.url) ?: return
        val embedding = encodeEmbedding(document.embedding) ?: return
        val database = helper.writableDatabase
        database.beginTransaction()
        try {
            val enabled = database.rawQuery(
                "SELECT 1 FROM profiles WHERE profile_id = ?",
                arrayOf(document.profileId),
            ).use { it.moveToFirst() }
            if (!enabled) return
            val newerVisitExists = database.rawQuery(
                "SELECT 1 FROM documents WHERE profile_id = ? AND url = ? AND visited_at > ? LIMIT 1",
                arrayOf(document.profileId, url, document.visitedAt.toString()),
            ).use { it.moveToFirst() }
            if (newerVisitExists) return
            database.insertWithOnConflict(
                "documents",
                null,
                ContentValues().apply {
                    put("profile_id", document.profileId)
                    put("url", url)
                    put("title", document.title.take(MAX_TITLE_CHARS))
                    put("excerpt", document.excerpt.take(MAX_EXCERPT_CHARS))
                    put("visited_at", document.visitedAt)
                    put("embedding", embedding)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            database.execSQL(
                "DELETE FROM documents WHERE profile_id = ? AND url IN (" +
                    "SELECT url FROM documents WHERE profile_id = ? " +
                    "ORDER BY visited_at DESC, url LIMIT -1 OFFSET ?)",
                arrayOf<Any>(document.profileId, document.profileId, MAX_DOCUMENTS_PER_PROFILE),
            )
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    @Synchronized
    fun deleteEntries(entries: Collection<HistoryEntry>) {
        val database = helper.writableDatabase
        database.beginTransaction()
        try {
            entries.forEach { entry ->
                database.delete(
                    "documents",
                    "profile_id = ? AND url = ?",
                    arrayOf(entry.profileId, CanonicalWebUrl.key(entry.url) ?: entry.url),
                )
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    @Synchronized
    fun deleteProfiles(profileIds: Set<String>, forgetEnabled: Boolean) {
        val database = helper.writableDatabase
        database.beginTransaction()
        try {
            profileIds.filter(::validProfile).forEach { profileId ->
                database.delete("documents", "profile_id = ?", arrayOf(profileId))
                if (forgetEnabled) database.delete("profiles", "profile_id = ?", arrayOf(profileId))
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    @Synchronized
    fun deleteRange(request: HistoryClearRequest) {
        val database = helper.writableDatabase
        database.beginTransaction()
        try {
            request.profileIds.filter(::validProfile).forEach { profileId ->
                database.delete(
                    "documents",
                    "profile_id = ? AND visited_at >= ? AND visited_at < ?",
                    arrayOf(
                        profileId,
                        request.sinceInclusiveMillis.toString(),
                        request.untilExclusiveMillis.toString(),
                    ),
                )
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    @Synchronized
    fun clearDocuments() {
        helper.writableDatabase.delete("documents", null, null)
    }

    @Synchronized
    fun reset(): Boolean = runCatching {
        helper.close()
        if (databaseFiles().any(File::exists)) SQLiteDatabase.deleteDatabase(databaseFile)
        databaseFiles().none(File::exists)
    }.getOrDefault(false)

    private fun databaseFiles(): List<File> = listOf(
        databaseFile,
        File("${databaseFile.path}-journal"),
        File("${databaseFile.path}-wal"),
        File("${databaseFile.path}-shm"),
    )

    private class DatabaseHelper(context: Context, path: String) : SQLiteOpenHelper(context, path, null, 1) {
        override fun onConfigure(db: SQLiteDatabase) {
            db.rawQuery("PRAGMA secure_delete = ON", null).use { it.moveToFirst() }
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE profiles (profile_id TEXT PRIMARY KEY NOT NULL)")
            db.execSQL(
                "CREATE TABLE documents (profile_id TEXT NOT NULL, url TEXT NOT NULL, " +
                    "title TEXT NOT NULL, excerpt TEXT NOT NULL, visited_at INTEGER NOT NULL, " +
                    "embedding BLOB NOT NULL, PRIMARY KEY(profile_id, url))",
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    companion object {
        const val DATABASE_NAME = "candy_constellations.db"
        const val MAX_DOCUMENTS_PER_PROFILE = 180
        const val MAX_TITLE_CHARS = 512
        const val MAX_EXCERPT_CHARS = 640
        const val EMBEDDING_DIMENSIONS = 512

        fun validProfile(profileId: String): Boolean = profileId.isNotBlank() && profileId.length <= 128

        private fun encodeEmbedding(values: List<Float>): ByteArray? {
            if (
                values.size != EMBEDDING_DIMENSIONS || values.any { !it.isFinite() } ||
                values.none { it != 0f }
            ) return null
            return ByteBuffer.allocate(values.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
                .apply { values.forEach(::putFloat) }.array()
        }

        private fun decodeEmbedding(bytes: ByteArray): List<Float>? {
            if (bytes.size != EMBEDDING_DIMENSIONS * Float.SIZE_BYTES) return null
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val values = List(EMBEDDING_DIMENSIONS) { buffer.float }
            return values.takeIf { vector -> vector.all(Float::isFinite) && vector.any { it != 0f } }
        }
    }
}
