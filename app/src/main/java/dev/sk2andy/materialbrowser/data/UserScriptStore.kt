package dev.sk2andy.materialbrowser.data

import android.content.Context
import dev.sk2andy.materialbrowser.browser.userscript.UserScript
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptParseResult
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptParser
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptRules
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

internal class UserScriptStore(context: Context) {
    private val atomicFile = BackedUpAtomicFileMigration.fromNoBackupDirectory(
        context = context,
        fileName = FILE_NAME,
        maxBytes = MAX_FILE_BYTES,
    )

    @Synchronized
    fun load(): List<UserScript> {
        return try {
            val bytes = atomicFile.openRead().use { input -> input.readNBytes(MAX_FILE_BYTES + 1) }
            if (bytes.size !in 1..MAX_FILE_BYTES) {
                atomicFile.delete()
                return emptyList()
            }
            val root = JSONObject(bytes.toString(StandardCharsets.UTF_8))
            check(root.optInt("version", -1) == FORMAT_VERSION)
            val values = root.getJSONArray("scripts")
            check(values.length() <= UserScriptParser.MAX_SCRIPTS)
            val scripts = buildList {
                for (index in 0 until values.length()) {
                    val item = values.getJSONObject(index)
                    val result = UserScriptParser.parse(
                        id = item.getString("id"),
                        source = item.getString("source"),
                        enabled = item.getBoolean("enabled"),
                        updatedAtMillis = item.getLong("updatedAtMillis"),
                    )
                    add((result as UserScriptParseResult.Accepted).script)
                }
            }
            check(UserScriptRules.isWithinCollectionBounds(scripts))
            scripts
        } catch (_: FileNotFoundException) {
            emptyList()
        } catch (_: Exception) {
            atomicFile.delete()
            emptyList()
        }
    }

    @Synchronized
    fun save(input: List<UserScript>): Boolean {
        if (!UserScriptRules.isWithinCollectionBounds(input)) return false
        val scripts = input.map { script ->
            val result = UserScriptParser.parse(
                id = script.id,
                source = script.source,
                enabled = script.enabled,
                updatedAtMillis = script.updatedAtMillis,
            )
            val parsed = (result as? UserScriptParseResult.Accepted)?.script ?: return false
            if (parsed != script) return false
            parsed
        }
        val root = JSONObject()
            .put("version", FORMAT_VERSION)
            .put(
                "scripts",
                JSONArray().also { array ->
                    scripts.forEach { script ->
                        array.put(
                            JSONObject()
                                .put("id", script.id)
                                .put("source", script.source)
                                .put("enabled", script.enabled)
                                .put("updatedAtMillis", script.updatedAtMillis),
                        )
                    }
                },
            )
        val bytes = root.toString().toByteArray(Charsets.UTF_8)
        if (bytes.size !in 1..MAX_FILE_BYTES) return false
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

    @Synchronized
    fun clear() = atomicFile.delete()

    internal companion object {
        const val FILE_NAME = "user_scripts.json"
        const val FORMAT_VERSION = 1
        // Keeps synchronous startup recovery bounded; unusually escape-heavy JSON fails closed.
        const val MAX_FILE_BYTES = 8 * 1_024 * 1_024
    }
}
