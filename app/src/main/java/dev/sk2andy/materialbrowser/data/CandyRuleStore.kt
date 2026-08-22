package dev.sk2andy.materialbrowser.data

import android.content.Context
import dev.sk2andy.materialbrowser.blocking.CANDY_RULE_FORMAT_VERSION
import dev.sk2andy.materialbrowser.blocking.CandyRule
import dev.sk2andy.materialbrowser.blocking.CandyRuleAction
import dev.sk2andy.materialbrowser.blocking.CandyRuleKind
import dev.sk2andy.materialbrowser.blocking.CandyRuleOrigin
import dev.sk2andy.materialbrowser.blocking.CandyRuleValidator
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileNotFoundException
import java.io.FileOutputStream

class CandyRuleStore(context: Context) {
    private val atomicFile = BackedUpAtomicFileMigration.fromNoBackupDirectory(
        context = context,
        fileName = FILE_NAME,
        maxBytes = MAX_FILE_BYTES,
    )

    @Synchronized
    fun load(): List<CandyRule> {
        return try {
            if (atomicFile.baseFile.length() !in 1..MAX_FILE_BYTES) {
                atomicFile.delete()
                return emptyList()
            }
            val root = atomicFile.openRead().bufferedReader().use { JSONObject(it.readText()) }
            if (root.optInt("version", -1) != CANDY_RULE_FORMAT_VERSION) {
                atomicFile.delete()
                return emptyList()
            }
            val values = root.optJSONArray("rules") ?: return emptyList()
            if (values.length() > CandyRuleValidator.MAX_RULES) {
                atomicFile.delete()
                return emptyList()
            }
            CandyRuleValidator.normalizeAll(
                buildList {
                    for (index in 0 until values.length()) {
                        val item = values.getJSONObject(index)
                        add(
                            CandyRule(
                            id = item.getString("id"),
                            action = CandyRuleAction.valueOf(item.getString("action")),
                            kind = CandyRuleKind.valueOf(item.getString("kind")),
                            requestHost = item.optNullableString("requestHost"),
                            firstPartyHost = item.optNullableString("firstPartyHost"),
                            cosmeticSelector = item.optNullableString("cosmeticSelector"),
                            profileId = item.optNullableString("profileId"),
                            group = item.optString("group", "Personal"),
                            origin = runCatching {
                                CandyRuleOrigin.valueOf(item.optString("origin"))
                            }.getOrDefault(CandyRuleOrigin.User),
                            sourceUrl = item.optNullableString("sourceUrl"),
                            updatedAtMillis = item.optLong("updatedAtMillis", 0L),
                            active = item.optBoolean("active", true),
                            hitCount = item.optInt("hitCount", 0),
                            ),
                        )
                    }
                },
            )
        } catch (_: FileNotFoundException) {
            emptyList()
        } catch (_: Exception) {
            atomicFile.delete()
            emptyList()
        }
    }

    @Synchronized
    fun save(input: Iterable<CandyRule>): Boolean {
        val rules = CandyRuleValidator.normalizeAll(input)
        val root = JSONObject()
            .put("version", CANDY_RULE_FORMAT_VERSION)
            .put(
                "rules",
                JSONArray().also { array ->
                    rules.forEach { rule ->
                        array.put(
                            JSONObject()
                                .put("id", rule.id)
                                .put("action", rule.action.name)
                                .put("kind", rule.kind.name)
                                .put("requestHost", rule.requestHost)
                                .put("firstPartyHost", rule.firstPartyHost)
                                .put("cosmeticSelector", rule.cosmeticSelector)
                                .put("profileId", rule.profileId)
                                .put("group", rule.group)
                                .put("origin", rule.origin.name)
                                .put("sourceUrl", rule.sourceUrl)
                                .put("updatedAtMillis", rule.updatedAtMillis)
                                .put("active", rule.active)
                                .put("hitCount", rule.hitCount),
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

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf(String::isNotEmpty)

    internal companion object {
        const val FILE_NAME = "candy_filter_rules.json"
        // Validator bounds make the worst-case 4,096-rule snapshot smaller than this.
        const val MAX_FILE_BYTES = 16 * 1_024 * 1_024
    }
}
