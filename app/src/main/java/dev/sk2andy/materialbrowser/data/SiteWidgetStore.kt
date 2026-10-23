package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import dev.sk2andy.materialbrowser.browser.isSynced
import dev.sk2andy.materialbrowser.browser.sitewidget.SiteWidgetCrop
import dev.sk2andy.materialbrowser.browser.sitewidget.SiteWidgetFormat
import dev.sk2andy.materialbrowser.browser.sitewidget.SiteWidgetSnapshot
import dev.sk2andy.materialbrowser.browser.sitewidget.SiteWidgetSnapshotRules
import dev.sk2andy.materialbrowser.browser.sitewidget.SiteWidgetSource
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import org.json.JSONObject

/** Local homescreen captures are deliberately excluded from backups and app data archives. */
internal class SiteWidgetStore(context: Context) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.noBackupFilesDir, DIRECTORY_NAME)

    fun save(snapshot: SiteWidgetSnapshot, bitmap: Bitmap): Boolean = synchronized(lock) {
        saveSnapshot(snapshot, bitmap, SiteWidgetSnapshotRules.MAX_SNAPSHOTS)
    }

    private fun saveSnapshot(snapshot: SiteWidgetSnapshot, bitmap: Bitmap, maximumSnapshots: Int): Boolean {
        val safe = SiteWidgetSnapshotRules.sanitize(snapshot) ?: return false
        if (
            !isAllowedProfile(safe.source.profileId) || bitmap.isRecycled ||
            bitmap.width !in 1..SiteWidgetSnapshotRules.MAX_BITMAP_DIMENSION ||
            bitmap.height !in 1..SiteWidgetSnapshotRules.MAX_BITMAP_DIMENSION ||
            !ensureDirectory()
        ) return false
        pruneDrafts(System.currentTimeMillis())
        if (
            snapshotFile(safe.id).baseFile.exists() ||
            snapshotIds().size >= maximumSnapshots
        ) return false
        val bytes = runCatching {
            ByteArrayOutputStream().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) return false
                output.toByteArray()
            }
        }.getOrNull() ?: return false
        if (bytes.size !in 1..MAX_IMAGE_BYTES) return false
        val image = imageFile(safe.id)
        if (!image.writeSafely { it.write(bytes) }) return false
        if (!writeJson(snapshotFile(safe.id), encode(safe))) {
            image.delete()
            return false
        }
        return true
    }

    fun load(id: String): SiteWidgetSnapshot? = synchronized(lock) { loadSnapshot(id) }

    fun loadBitmap(id: String): Bitmap? = synchronized(lock) {
        val snapshot = loadSnapshot(id) ?: return null
        val file = imageFile(snapshot.id)
        val bytes = readBytes(file, MAX_IMAGE_BYTES) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (
            bounds.outWidth !in 1..SiteWidgetSnapshotRules.MAX_BITMAP_DIMENSION ||
            bounds.outHeight !in 1..SiteWidgetSnapshotRules.MAX_BITMAP_DIMENSION
        ) return null
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
    }

    fun bind(widgetId: Int, id: String): Boolean = synchronized(lock) {
        if (widgetId <= 0) return false
        val snapshot = loadSnapshot(id) ?: return false
        val bindings = readBindings()
        if (bindings.size >= MAX_BINDINGS && widgetId !in bindings) return false
        val previousId = bindings.put(widgetId, snapshot.id)
        if (!writeBindings(bindings)) return false
        if (previousId != null && previousId !in bindings.values) deleteSnapshot(previousId)
        true
    }

    fun snapshotForWidget(widgetId: Int): SiteWidgetSnapshot? = synchronized(lock) {
        readBindings()[widgetId]?.let(::loadSnapshot)
    }

    fun boundSnapshots(): Map<Int, SiteWidgetSnapshot> = synchronized(lock) {
        readBindings().mapNotNull { (widgetId, id) -> loadSnapshot(id)?.let { widgetId to it } }.toMap()
    }

    fun launcherCellAspectRatio(launcherPackage: String): Float? = synchronized(lock) {
        if (!isValidLauncherPackage(launcherPackage)) return null
        val ratios = readJson(sizingFile())?.takeIf { it.optInt("version") == CURRENT_VERSION }
            ?.optJSONObject("launchers") ?: return null
        ratios.optDouble(launcherPackage, Double.NaN).toFloat()
            .takeIf { it.isFinite() && it in MINIMUM_CELL_ASPECT_RATIO..MAXIMUM_CELL_ASPECT_RATIO }
    }

    fun saveLauncherCellAspectRatio(launcherPackage: String, ratio: Float): Boolean = synchronized(lock) {
        if (!isValidLauncherPackage(launcherPackage) || ratio !in MINIMUM_CELL_ASPECT_RATIO..MAXIMUM_CELL_ASPECT_RATIO) {
            return false
        }
        if (!ensureDirectory()) return false
        val ratios = readJson(sizingFile())?.takeIf { it.optInt("version") == CURRENT_VERSION }
            ?.optJSONObject("launchers") ?: JSONObject()
        if (!ratios.has(launcherPackage) && ratios.length() >= MAX_LEARNED_LAUNCHERS) {
            ratios.keys().asSequence().firstOrNull()?.let(ratios::remove)
        }
        ratios.put(launcherPackage, ratio.toDouble())
        writeJson(sizingFile(), JSONObject().put("version", CURRENT_VERSION).put("launchers", ratios))
    }

    fun replaceIfCurrent(
        widgetId: Int,
        expectedSnapshotId: String,
        replacement: SiteWidgetSnapshot,
        bitmap: Bitmap,
    ): Boolean = synchronized(lock) {
        val current = snapshotForWidget(widgetId) ?: return false
        if (
            current.id != expectedSnapshotId || current.source != replacement.source ||
            current.format != replacement.format || current.crop != replacement.crop ||
            current.refreshIntervalMinutes != replacement.refreshIntervalMinutes
        ) return false
        val profile = BrowserSessionStore(appContext).loadProfiles().first.firstOrNull { it.id == current.source.profileId }
            ?: return false
        if (profile.isolationEnabled != current.source.isolationEnabled) return false
        // A replacement may briefly use one extra slot only when its commit frees the previous slot.
        val replacesLastBinding = readBindings().values.count { it == current.id } == 1
        val maximumSnapshots = SiteWidgetSnapshotRules.MAX_SNAPSHOTS + if (replacesLastBinding) 1 else 0
        if (!saveSnapshot(replacement, bitmap, maximumSnapshots)) return false
        if (bind(widgetId, replacement.id)) return true
        deleteSnapshot(replacement.id)
        false
    }

    fun deleteWidget(widgetId: Int): Unit = synchronized(lock) {
        val bindings = readBindings()
        val id = bindings.remove(widgetId) ?: return
        if (writeBindings(bindings) && id !in bindings.values) deleteSnapshot(id)
    }

    fun delete(id: String): Unit = synchronized(lock) {
        val safeId = SiteWidgetSnapshotRules.normalizedId(id) ?: return
        val bindings = readBindings().filterValues { it != safeId }
        if (writeBindings(bindings)) deleteSnapshot(safeId)
    }

    fun invalidateProfiles(allowedProfileIds: Set<String>): Unit = synchronized(lock) {
        val allowed = allowedProfileIds.intersect(allowedProfiles())
        val validIds = snapshotIds().filter { id ->
            loadSnapshot(id)?.source?.profileId in allowed
        }.toSet()
        val bindings = readBindings().filterValues(validIds::contains)
        if (!writeBindings(bindings)) return
        snapshotIds().filterNot(validIds::contains).forEach(::deleteSnapshot)
    }

    private fun loadSnapshot(id: String): SiteWidgetSnapshot? {
        val safeId = SiteWidgetSnapshotRules.normalizedId(id) ?: return null
        val root = readJson(snapshotFile(safeId)) ?: return null
        if (root.optInt("version") != CURRENT_VERSION) return null
        val snapshot = runCatching {
            val source = root.getJSONObject("source")
            val crop = root.getJSONObject("crop")
            SiteWidgetSnapshot(
                id = root.getString("id"),
                source = SiteWidgetSource(
                    tabId = source.getString("tabId"),
                    profileId = source.getString("profileId"),
                    url = source.getString("url"),
                    title = source.getString("title"),
                    navigationGeneration = source.getInt("navigationGeneration"),
                    isPrivate = source.optBoolean("isPrivate", true),
                    engineId = source.optString("engineId", ""),
                    viewportWidthPx = source.optInt("viewportWidthPx", 0),
                    viewportHeightPx = source.optInt("viewportHeightPx", 0),
                    scrollY = source.optInt("scrollY", 0),
                    isolationEnabled = source.optBoolean("isolationEnabled", false),
                    desktopMode = source.optBoolean("desktopMode", false),
                ),
                format = SiteWidgetFormat.valueOf(root.getString("format")),
                crop = SiteWidgetCrop(
                    left = crop.getDouble("left").toFloat(),
                    top = crop.getDouble("top").toFloat(),
                    width = crop.getDouble("width").toFloat(),
                    height = crop.getDouble("height").toFloat(),
                ),
                createdAtMillis = root.getLong("createdAtMillis"),
                refreshIntervalMinutes = root.optInt("refreshIntervalMinutes", 0),
            )
        }.getOrNull()?.let(SiteWidgetSnapshotRules::sanitize) ?: return null
        return snapshot.takeIf {
            it.id == safeId && isAllowedProfile(it.source.profileId) && imageFile(safeId).baseFile.isFile
        }
    }

    private fun encode(snapshot: SiteWidgetSnapshot): JSONObject = JSONObject()
        .put("version", CURRENT_VERSION)
        .put("id", snapshot.id)
        .put("source", JSONObject()
            .put("tabId", snapshot.source.tabId)
            .put("profileId", snapshot.source.profileId)
            .put("url", snapshot.source.url)
            .put("title", snapshot.source.title)
            .put("navigationGeneration", snapshot.source.navigationGeneration)
            .put("isPrivate", snapshot.source.isPrivate)
            .put("engineId", snapshot.source.engineId)
            .put("viewportWidthPx", snapshot.source.viewportWidthPx)
            .put("viewportHeightPx", snapshot.source.viewportHeightPx)
            .put("scrollY", snapshot.source.scrollY)
            .put("isolationEnabled", snapshot.source.isolationEnabled)
            .put("desktopMode", snapshot.source.desktopMode))
        .put("format", snapshot.format.name)
        .put("crop", JSONObject()
            .put("left", snapshot.crop.left.toDouble())
            .put("top", snapshot.crop.top.toDouble())
            .put("width", snapshot.crop.width.toDouble())
            .put("height", snapshot.crop.height.toDouble()))
        .put("createdAtMillis", snapshot.createdAtMillis)
        .put("refreshIntervalMinutes", snapshot.refreshIntervalMinutes)

    private fun readBindings(): MutableMap<Int, String> {
        val values = readJson(bindingsFile())?.takeIf { it.optInt("version") == CURRENT_VERSION }
            ?.optJSONObject("bindings") ?: return mutableMapOf()
        return buildMap {
            values.keys().asSequence().take(MAX_BINDINGS).forEach { key ->
                val widgetId = key.toIntOrNull()?.takeIf { it > 0 } ?: return@forEach
                val id = SiteWidgetSnapshotRules.normalizedId(values.optString(key)) ?: return@forEach
                put(widgetId, id)
            }
        }.toMutableMap()
    }

    private fun writeBindings(bindings: Map<Int, String>): Boolean {
        if (!ensureDirectory()) return false
        val values = JSONObject()
        bindings.toSortedMap().forEach { (widgetId, id) -> values.put(widgetId.toString(), id) }
        return writeJson(bindingsFile(), JSONObject().put("version", CURRENT_VERSION).put("bindings", values))
    }

    private fun allowedProfiles(): Set<String> = BrowserSessionStore(appContext).loadProfiles().first
        .filter { !it.isSynced && it.protection == null }
        .map { it.id }.toSet()

    private fun isAllowedProfile(profileId: String): Boolean = profileId in allowedProfiles()

    private fun pruneDrafts(nowMillis: Long) {
        val boundIds = readBindings().values.toSet()
        val cutoffMillis = nowMillis - DRAFT_LIFETIME_MILLIS
        snapshotIds().filterNot(boundIds::contains).forEach { id ->
            val snapshot = loadSnapshot(id)
            if (snapshot == null || snapshot.createdAtMillis < cutoffMillis) deleteSnapshot(id)
        }
    }

    private fun snapshotIds(): List<String> = directory.listFiles().orEmpty()
        .filter { it.extension == "json" && it.name != BINDINGS_FILE }
        .mapNotNull { SiteWidgetSnapshotRules.normalizedId(it.nameWithoutExtension) }

    private fun deleteSnapshot(id: String) {
        snapshotFile(id).delete()
        imageFile(id).delete()
    }

    private fun snapshotFile(id: String): AtomicFile = AtomicFile(File(directory, "$id.json"))

    private fun imageFile(id: String): AtomicFile = AtomicFile(File(directory, "$id.png"))

    private fun bindingsFile(): AtomicFile = AtomicFile(File(directory, BINDINGS_FILE))

    private fun sizingFile(): AtomicFile = AtomicFile(File(directory, SIZING_FILE))

    private fun isValidLauncherPackage(value: String): Boolean =
        value.length in 3..255 && value.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))

    private fun ensureDirectory(): Boolean = directory.isDirectory || directory.mkdirs()

    private fun readJson(file: AtomicFile): JSONObject? = readBytes(file, MAX_JSON_BYTES)?.let { bytes ->
        runCatching { JSONObject(bytes.toString(StandardCharsets.UTF_8)) }.getOrNull()
    }

    private fun readBytes(file: AtomicFile, limit: Int): ByteArray? = runCatching {
        file.openRead().use { it.readNBytes(limit + 1) }.takeIf { it.size in 1..limit }
    }.getOrNull()

    private fun writeJson(file: AtomicFile, value: JSONObject): Boolean {
        val bytes = value.toString().toByteArray(StandardCharsets.UTF_8)
        return bytes.size <= MAX_JSON_BYTES && file.writeSafely { it.write(bytes) }
    }

    private companion object {
        val lock = Any()
        const val DIRECTORY_NAME = "site_widget_snapshots_v1"
        const val BINDINGS_FILE = "bindings.json"
        const val SIZING_FILE = "launcher_sizing.json"
        const val CURRENT_VERSION = 1
        const val MAX_JSON_BYTES = 32 * 1_024
        const val MAX_IMAGE_BYTES = 3 * 1_024 * 1_024
        const val MAX_BINDINGS = 64
        const val MAX_LEARNED_LAUNCHERS = 16
        const val MINIMUM_CELL_ASPECT_RATIO = 0.5f
        const val MAXIMUM_CELL_ASPECT_RATIO = 2f
        const val DRAFT_LIFETIME_MILLIS = 24L * 60L * 60L * 1_000L
    }
}
