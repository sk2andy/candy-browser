package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.util.AtomicFile
import dev.sk2andy.materialbrowser.browser.gecko.GeckoSessionStateSnapshot
import dev.sk2andy.materialbrowser.browser.gecko.GeckoSessionStateSnapshotRules
import java.io.File
import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets
import org.json.JSONObject

/** Device-local Gecko state. Archive code deliberately excludes this engine-specific directory. */
internal class GeckoSessionStateStore(context: Context) {
    private val files = AtomicTabFileDirectory(
        directory = File(context.noBackupFilesDir, DIRECTORY_NAME),
        extension = FILE_EXTENSION,
    )

    fun load(tabId: String): GeckoSessionStateSnapshot? {
        val file = files.fileFor(tabId) ?: return null
        val bytes = try {
            AtomicFile(file).openRead().use { input ->
                if (file.length() !in 1..MAX_FILE_BYTES) {
                    AtomicFile(file).delete()
                    return null
                }
                input.readBytes()
            }
        } catch (_: FileNotFoundException) {
            return null
        } catch (_: Exception) {
            AtomicFile(file).delete()
            return null
        }
        return runCatching {
            val json = JSONObject(String(bytes, StandardCharsets.UTF_8))
            GeckoSessionStateSnapshot(
                tabId = json.getString("tabId"),
                profileId = json.getString("profileId"),
                encodedState = json.getString("encodedState"),
                formatVersion = json.getInt("formatVersion"),
            )
        }.getOrNull()?.also { snapshot ->
            if (
                GeckoSessionStateSnapshotRules.restoreDecision(
                    snapshot = snapshot,
                    tabId = tabId,
                    profileId = snapshot.profileId,
                    isPrivate = false,
                ) !is dev.sk2andy.materialbrowser.browser.gecko.GeckoSessionStateRestoreDecision.Restore
            ) {
                AtomicFile(file).delete()
                return null
            }
        }
    }

    fun save(snapshot: GeckoSessionStateSnapshot): Boolean {
        val safeSnapshot = GeckoSessionStateSnapshotRules.forPersistence(
            tabId = snapshot.tabId,
            profileId = snapshot.profileId,
            isPrivate = false,
            encodedState = snapshot.encodedState,
        ) ?: return false
        if (safeSnapshot.formatVersion != snapshot.formatVersion || !files.ensureExists()) return false
        val target = files.fileFor(safeSnapshot.tabId) ?: return false
        val bytes = JSONObject()
            .put("formatVersion", safeSnapshot.formatVersion)
            .put("tabId", safeSnapshot.tabId)
            .put("profileId", safeSnapshot.profileId)
            .put("encodedState", safeSnapshot.encodedState)
            .toString()
            .toByteArray(StandardCharsets.UTF_8)
        if (bytes.size !in 1..MAX_FILE_BYTES) return false
        return AtomicFile(target).writeSafely { output -> output.write(bytes) }
    }

    fun delete(tabId: String) = files.delete(tabId)

    fun prune(validTabIds: Set<String>) = files.prune(validTabIds)

    fun clear() = files.clearAndRemoveDirectory()

    private companion object {
        const val DIRECTORY_NAME = "gecko_session_states"
        const val FILE_EXTENSION = "json"
        const val MAX_FILE_BYTES = 8 * 1_024 * 1_024
    }
}
