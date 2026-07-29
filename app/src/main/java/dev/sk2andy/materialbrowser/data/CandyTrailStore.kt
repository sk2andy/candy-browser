package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.util.AtomicFile
import dev.sk2andy.materialbrowser.browser.CandyTrail
import dev.sk2andy.materialbrowser.browser.CandyTrailFork
import dev.sk2andy.materialbrowser.browser.CandyTrailForkMigrationRules
import dev.sk2andy.materialbrowser.browser.CandyTrailForkRules
import dev.sk2andy.materialbrowser.browser.CandyTrailNode
import dev.sk2andy.materialbrowser.browser.CandyTrailRules
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.util.UUID

class CandyTrailStore(context: Context) {
    private val directory = File(context.noBackupFilesDir, DIRECTORY_NAME)

    fun load(tabId: String): CandyTrail? {
        val target = fileFor(tabId) ?: return null
        val atomicFile = AtomicFile(target)
        return try {
            if (atomicFile.baseFile.length() !in 1..MAX_FILE_SIZE_BYTES) {
                atomicFile.delete()
                return null
            }
            val root = atomicFile.openRead().bufferedReader().use { reader ->
                JSONObject(reader.readText())
            }
            val version = root.optInt("version", -1)
            if (
                !CandyTrailForkMigrationRules.supportsVersion(version) ||
                root.optString("tabId") != tabId
            ) {
                atomicFile.delete()
                return null
            }
            val nodesJson = root.getJSONArray("nodes")
            if (nodesJson.length() > CandyTrailRules.MAX_NODES) {
                atomicFile.delete()
                return null
            }
            val nodes = buildList {
                for (index in 0 until nodesJson.length()) {
                    val item = nodesJson.getJSONObject(index)
                    add(
                        CandyTrailNode(
                            id = item.getString("id"),
                            parentId = if (item.isNull("parentId")) null else item.getString("parentId"),
                            url = item.getString("url"),
                            title = item.optString("title", ""),
                            visitedAt = item.optLong("visitedAt", 0L),
                        ),
                    )
                }
            }
            val forksJson = root.optJSONArray("forks") ?: JSONArray()
            if (forksJson.length() > CandyTrailForkRules.MAX_FORKS) {
                atomicFile.delete()
                return null
            }
            val forks = buildList {
                for (index in 0 until forksJson.length()) {
                    val item = forksJson.getJSONObject(index)
                    add(
                        CandyTrailFork(
                            id = item.getString("id"),
                            originTabId = item.getString("originTabId"),
                            originNodeId = item.getString("originNodeId"),
                            destinationTabId = item.optString("destinationTabId")
                                .takeIf(String::isNotBlank),
                            profileId = item.getString("profileId"),
                            isIncognito = item.optBoolean("isIncognito", false),
                            url = item.getString("url"),
                            title = item.optString("title", ""),
                            createdAt = item.optLong("createdAt", 0L),
                            updatedAt = item.optLong("updatedAt", 0L),
                            lifecycle = CandyTrailForkMigrationRules.lifecycleFromWire(
                                value = item.optString("lifecycle"),
                                destinationTabId = item.optString("destinationTabId")
                                    .takeIf(String::isNotBlank),
                            ),
                        ),
                    )
                }
            }
            CandyTrailRules.normalized(
                CandyTrail(
                    tabId = tabId,
                    nodes = nodes,
                    currentNodeId = root.optString("currentNodeId")
                        .takeIf(String::isNotBlank),
                    nextOrdinal = root.optLong("nextOrdinal", 0L).coerceAtLeast(0L),
                    forks = forks,
                    nextForkOrdinal = root.optLong("nextForkOrdinal", 0L).coerceAtLeast(0L),
                ),
            )
        } catch (_: FileNotFoundException) {
            null
        } catch (_: Exception) {
            atomicFile.delete()
            null
        }
    }

    fun save(tabId: String, trail: CandyTrail): Boolean {
        if (trail.tabId != tabId || (!directory.exists() && !directory.mkdirs())) return false
        val normalized = CandyTrailRules.normalized(trail)
        val root = JSONObject()
            .put("version", FORMAT_VERSION)
            .put("tabId", tabId)
            .put("currentNodeId", normalized.currentNodeId)
            .put("nextOrdinal", normalized.nextOrdinal)
            .put("nextForkOrdinal", normalized.nextForkOrdinal)
            .put(
                "nodes",
                JSONArray().also { array ->
                    normalized.nodes.forEach { node ->
                        array.put(
                            JSONObject()
                                .put("id", node.id)
                                .put("parentId", node.parentId)
                                .put("url", node.url)
                                .put("title", node.title)
                                .put("visitedAt", node.visitedAt),
                        )
                    }
                },
            )
            .put(
                "forks",
                JSONArray().also { array ->
                    normalized.forks.forEach { fork ->
                        array.put(
                            JSONObject()
                                .put("id", fork.id)
                                .put("originTabId", fork.originTabId)
                                .put("originNodeId", fork.originNodeId)
                                .put("destinationTabId", fork.destinationTabId)
                                .put("profileId", fork.profileId)
                                .put("isIncognito", fork.isIncognito)
                                .put("url", fork.url)
                                .put("title", fork.title)
                                .put("createdAt", fork.createdAt)
                                .put("updatedAt", fork.updatedAt)
                                .put(
                                    "lifecycle",
                                    CandyTrailForkMigrationRules.lifecycleWireValue(fork.lifecycle),
                                ),
                        )
                    }
                },
            )
        val bytes = root.toString().toByteArray(Charsets.UTF_8)
        if (bytes.size !in 1..MAX_FILE_SIZE_BYTES) return false
        val atomicFile = AtomicFile(fileFor(tabId) ?: return false)
        var output: FileOutputStream? = null
        return try {
            output = atomicFile.startWrite()
            output.write(bytes)
            atomicFile.finishWrite(output)
            true
        } catch (_: Exception) {
            output?.let(atomicFile::failWrite)
            false
        }
    }

    fun delete(tabId: String) {
        fileFor(tabId)?.let { target -> AtomicFile(target).delete() }
    }

    fun prune(validTabIds: Set<String>) {
        val validNames = validTabIds.mapNotNull(::candyTrailFileName).toSet()
        val orphanBaseNames = directory.listFiles()
            ?.map { file -> candyTrailAtomicBaseName(file.name) }
            ?.filterNot(validNames::contains)
            ?.toSet()
            .orEmpty()
        orphanBaseNames.forEach { baseName -> AtomicFile(File(directory, baseName)).delete() }
    }

    fun clear() {
        directory.listFiles()?.forEach(File::delete)
    }

    internal fun fileFor(tabId: String): File? = candyTrailFileName(tabId)?.let { File(directory, it) }

    internal companion object {
        const val DIRECTORY_NAME = "candy_trails"
        const val FORMAT_VERSION = CandyTrailForkMigrationRules.CURRENT_FORMAT_VERSION
        const val MAX_FILE_SIZE_BYTES = 256 * 1_024
    }
}

internal fun candyTrailFileName(tabId: String): String? = runCatching {
    "${UUID.fromString(tabId)}.json"
}.getOrNull()

private fun candyTrailAtomicBaseName(fileName: String): String = when {
    fileName.endsWith(".new") -> fileName.removeSuffix(".new")
    fileName.endsWith(".bak") -> fileName.removeSuffix(".bak")
    else -> fileName
}
