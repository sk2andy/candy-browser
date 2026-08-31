package dev.sk2andy.materialbrowser.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Handler
import android.os.Looper
import dev.sk2andy.materialbrowser.recall.RecallDocument
import dev.sk2andy.materialbrowser.recall.RecallExtractionParser
import dev.sk2andy.materialbrowser.recall.RecallMatch
import dev.sk2andy.materialbrowser.recall.RecallRules
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

internal class RecallRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val databaseFile = File(appContext.noBackupFilesDir, DATABASE_NAME)
    private val helper = RecallDatabaseHelper(appContext, databaseFile.absolutePath)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "recall-index-io")
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cleanupEpoch = AtomicLong()

    fun index(document: RecallDocument) {
        val sanitized = RecallRules.sanitizeDocument(document) ?: return
        executor.execute { indexSanitized(sanitized) }
    }

    fun indexExtracted(
        webViewResult: String?,
        profileId: String,
        expectedUrl: String,
        expectedCleanupEpoch: Long,
        visitedAt: Long,
    ) {
        executor.execute {
            if (cleanupEpoch.get() != expectedCleanupEpoch) return@execute
            val document = RecallExtractionParser.parse(
                webViewResult = webViewResult,
                profileId = profileId,
                expectedUrl = expectedUrl,
                visitedAt = visitedAt,
            ) ?: return@execute
            if (cleanupEpoch.get() != expectedCleanupEpoch) return@execute
            indexSanitized(document)
        }
    }

    fun captureCleanupEpoch(): Long = cleanupEpoch.get()

    fun search(
        profileIds: Set<String>,
        query: String,
        limit: Int,
        onComplete: (List<RecallMatch>) -> Unit,
    ) {
        val safeProfiles = profileIds.filter(::isSafeProfileId).distinct().toSet()
        val expression = RecallRules.matchExpression(query)
        val safeLimit = limit.coerceIn(0, RecallRules.MAX_HISTORY_RESULTS)
        if (safeProfiles.isEmpty() || expression == null || safeLimit == 0) {
            mainHandler.post { onComplete(emptyList()) }
            return
        }
        executor.execute {
            val matches = runDatabaseOperation(emptyList()) {
                searchDatabase(
                    database = helper.readableDatabase,
                    profileIds = safeProfiles,
                    expression = expression,
                    limit = safeLimit,
                )
            }
            mainHandler.post { onComplete(matches) }
        }
    }

    fun deleteEntries(entries: Collection<HistoryEntry>): Boolean {
        if (entries.isEmpty()) return true
        cleanupEpoch.incrementAndGet()
        return runCleanupSerialized {
            val database = helper.writableDatabase
            database.beginTransaction()
            try {
                entries.forEach { entry ->
                    database.delete(
                        TABLE_RECALL,
                        "$COLUMN_PROFILE_ID = ? AND $COLUMN_URL = ?",
                        arrayOf(entry.profileId, CanonicalWebUrl.key(entry.url) ?: entry.url),
                    )
                }
                database.setTransactionSuccessful()
                true
            } finally {
                database.endTransaction()
            }
        }
    }

    fun deleteProfiles(profileIds: Set<String>): Boolean = deleteWhereValues(
        column = COLUMN_PROFILE_ID,
        values = profileIds,
    )

    fun deleteProfilesAsync(
        profileIds: Set<String>,
        onComplete: (Boolean) -> Unit,
    ) {
        val safeProfiles = profileIds.filter(::isSafeProfileId).toSet()
        cleanupEpoch.incrementAndGet()
        executor.execute {
            val deleted = if (safeProfiles.isEmpty()) {
                true
            } else {
                runCleanup {
                    val placeholders = safeProfiles.joinToString(",") { "?" }
                    helper.writableDatabase.delete(
                        TABLE_RECALL,
                        "$COLUMN_PROFILE_ID IN ($placeholders)",
                        safeProfiles.toTypedArray(),
                    )
                    true
                }
            }
            mainHandler.post { onComplete(deleted) }
        }
    }

    fun deleteRange(request: HistoryClearRequest): Boolean {
        cleanupEpoch.incrementAndGet()
        return runCleanupSerialized {
            val profiles = request.profileIds.filter(::isSafeProfileId)
            if (profiles.isEmpty()) return@runCleanupSerialized true
            val placeholders = profiles.joinToString(",") { "?" }
            val arguments = profiles +
                request.sinceInclusiveMillis.toString() + request.untilExclusiveMillis.toString()
            helper.writableDatabase.delete(
                TABLE_RECALL,
                "$COLUMN_PROFILE_ID IN ($placeholders) AND " +
                    "CAST($COLUMN_VISITED_AT AS INTEGER) >= ? AND " +
                    "CAST($COLUMN_VISITED_AT AS INTEGER) < ?",
                arguments.toTypedArray(),
            )
            true
        }
    }

    fun clear(): Boolean {
        cleanupEpoch.incrementAndGet()
        return runCleanupSerialized(::resetDatabase)
    }

    fun clearAsync(onComplete: (Boolean) -> Unit = {}) {
        cleanupEpoch.incrementAndGet()
        executor.execute {
            val cleared = runCleanup(::resetDatabase)
            mainHandler.post { onComplete(cleared) }
        }
    }

    internal fun clearForTesting() {
        clear()
    }

    internal fun awaitIdleForTesting(): Boolean = runCatching {
        executor.submit<Boolean> { true }.get()
    }.getOrDefault(false)

    internal fun corruptStorageForTesting(): Boolean = runCatching {
        executor.submit<Boolean> {
            helper.close()
            SQLiteDatabase.deleteDatabase(databaseFile)
            databaseFile.writeText("not a sqlite database")
            true
        }.get()
    }.getOrDefault(false)

    internal fun storageExistsForTesting(): Boolean = databaseFiles().any(File::exists)

    private fun deleteWhereValues(column: String, values: Set<String>): Boolean =
        run {
            cleanupEpoch.incrementAndGet()
            runCleanupSerialized {
                val safeValues = values.filter(::isSafeProfileId)
                if (safeValues.isEmpty()) return@runCleanupSerialized true
                val placeholders = safeValues.joinToString(",") { "?" }
                helper.writableDatabase.delete(
                    TABLE_RECALL,
                    "$column IN ($placeholders)",
                    safeValues.toTypedArray(),
                )
                true
            }
        }

    private fun isSafeProfileId(profileId: String): Boolean =
        profileId.isNotBlank() && profileId.length <= RecallRules.MAX_PROFILE_ID_CHARS

    private fun indexSanitized(document: RecallDocument) {
        runDatabaseOperation {
            val database = helper.writableDatabase
            database.beginTransaction()
            try {
                database.delete(
                    TABLE_RECALL,
                    "$COLUMN_PROFILE_ID = ? AND $COLUMN_URL = ?",
                    arrayOf(document.profileId, document.url),
                )
                database.insertOrThrow(TABLE_RECALL, null, document.toValues())
                prune(database)
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }
    }

    private fun searchDatabase(
        database: SQLiteDatabase,
        profileIds: Set<String>,
        expression: String,
        limit: Int,
    ): List<RecallMatch> {
        val placeholders = profileIds.joinToString(",") { "?" }
        val arguments = profileIds.toList() + expression + SEARCH_CANDIDATE_LIMIT.toString()
        val candidates = database.rawQuery(
            "SELECT $COLUMN_PROFILE_ID, $COLUMN_URL, $COLUMN_TITLE, " +
                "snippet($TABLE_RECALL, '[', ']', ' … ', 3, 24), " +
                "$COLUMN_VISITED_AT, matchinfo($TABLE_RECALL, 'pcx') " +
                "FROM $TABLE_RECALL WHERE $COLUMN_PROFILE_ID IN ($placeholders) " +
                "AND $TABLE_RECALL MATCH ? LIMIT ?",
            arguments.toTypedArray(),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toMatch())
            }
        }
        return candidates.sortedWith(
            compareByDescending<RecallMatch>(RecallMatch::score)
                .thenByDescending(RecallMatch::visitedAt)
                .thenBy(RecallMatch::profileId)
                .thenBy(RecallMatch::url),
        ).take(limit)
    }

    private fun Cursor.toMatch(): RecallMatch = RecallMatch(
        profileId = getString(0),
        url = getString(1),
        title = getString(2),
        excerpt = getString(3).orEmpty().take(RecallRules.MAX_EXCERPT_CHARS),
        visitedAt = getString(4).toLongOrNull() ?: 0L,
        score = lexicalScore(getBlob(5)),
    )

    private fun lexicalScore(matchInfo: ByteArray): Double {
        val values = ByteBuffer.wrap(matchInfo).order(ByteOrder.nativeOrder()).asIntBuffer()
        if (values.remaining() < 2) return 0.0
        val phraseCount = values.get()
        val columnCount = values.get()
        var score = 0.0
        repeat(phraseCount) {
            repeat(columnCount) { column ->
                if (values.remaining() < 3) return score
                val rowHits = values.get()
                values.get()
                val documentsWithHits = values.get()
                val weight = when (column) {
                    2 -> TITLE_WEIGHT
                    3 -> BODY_WEIGHT
                    else -> 0.0
                }
                score += rowHits * weight / (documentsWithHits + 1.0)
            }
        }
        return score
    }

    private fun RecallDocument.toValues(): ContentValues = ContentValues().apply {
        put(COLUMN_PROFILE_ID, profileId)
        put(COLUMN_URL, url)
        put(COLUMN_TITLE, title)
        put(COLUMN_BODY, text)
        put(COLUMN_VISITED_AT, visitedAt.toString())
    }

    private fun prune(database: SQLiteDatabase) {
        database.execSQL(
            "DELETE FROM $TABLE_RECALL WHERE rowid IN (" +
                "SELECT rowid FROM $TABLE_RECALL ORDER BY " +
                "CAST($COLUMN_VISITED_AT AS INTEGER) DESC, rowid DESC LIMIT -1 OFFSET ?)",
            arrayOf(RecallRules.MAX_ENTRIES),
        )
    }

    private fun <T> runDatabaseOperation(
        fallback: T,
        operation: () -> T,
    ): T = runCatching(operation).getOrElse { fallback }

    private fun runDatabaseOperation(operation: () -> Unit) {
        runCatching(operation)
    }

    private fun runCleanupSerialized(operation: () -> Boolean): Boolean = runCatching {
        executor.submit<Boolean> { runCleanup(operation) }.get()
    }.getOrDefault(false)

    private fun runCleanup(operation: () -> Boolean): Boolean = runCatching(operation)
        .getOrElse { resetDatabase() }

    private fun resetDatabase(): Boolean = runCatching {
        helper.close()
        if (databaseFiles().none(File::exists)) true else SQLiteDatabase.deleteDatabase(databaseFile)
    }.getOrDefault(false)

    private fun databaseFiles(): List<File> = listOf(
        databaseFile,
        File(databaseFile.path + "-journal"),
        File(databaseFile.path + "-shm"),
        File(databaseFile.path + "-wal"),
    )

    private class RecallDatabaseHelper(context: Context, path: String) :
        SQLiteOpenHelper(context, path, null, DATABASE_VERSION) {
        override fun onConfigure(database: SQLiteDatabase) {
            database.rawQuery("PRAGMA secure_delete = ON", null).use { cursor ->
                cursor.moveToFirst()
            }
        }

        override fun onCreate(database: SQLiteDatabase) {
            database.execSQL(
                "CREATE VIRTUAL TABLE $TABLE_RECALL USING fts4(" +
                    "$COLUMN_PROFILE_ID, $COLUMN_URL, $COLUMN_TITLE, $COLUMN_BODY, " +
                    "$COLUMN_VISITED_AT, notindexed=$COLUMN_PROFILE_ID, " +
                    "notindexed=$COLUMN_URL, notindexed=$COLUMN_VISITED_AT, tokenize=unicode61)",
            )
        }

        override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            database.execSQL("DROP TABLE IF EXISTS $TABLE_RECALL")
            onCreate(database)
        }
    }

    companion object {
        @Volatile
        private var instance: RecallRepository? = null

        fun get(context: Context): RecallRepository = instance ?: synchronized(this) {
            instance ?: RecallRepository(context.applicationContext).also { instance = it }
        }

        fun deleteStorage(context: Context): Boolean {
            instance?.let { repository ->
                repository.cleanupEpoch.incrementAndGet()
                return repository.runCleanupSerialized(repository::resetDatabase)
            }
            val database = File(context.noBackupFilesDir, DATABASE_NAME)
            val files = listOf(
                database,
                File(database.path + "-journal"),
                File(database.path + "-shm"),
                File(database.path + "-wal"),
            )
            if (files.none(File::exists)) return true
            return SQLiteDatabase.deleteDatabase(database)
        }

        private const val DATABASE_NAME = "candy_recall.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_RECALL = "recall_documents"
        private const val COLUMN_PROFILE_ID = "profile_id"
        private const val COLUMN_URL = "url"
        private const val COLUMN_TITLE = "title"
        private const val COLUMN_BODY = "body"
        private const val COLUMN_VISITED_AT = "visited_at"
        private const val SEARCH_CANDIDATE_LIMIT = RecallRules.MAX_ENTRIES
        private const val TITLE_WEIGHT = 5.0
        private const val BODY_WEIGHT = 1.0
    }
}
