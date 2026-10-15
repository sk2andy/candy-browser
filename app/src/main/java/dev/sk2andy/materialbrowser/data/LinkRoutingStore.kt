package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.util.AtomicFile
import dev.sk2andy.materialbrowser.browser.integration.linkrouting.LinkRoutingRule
import dev.sk2andy.materialbrowser.browser.integration.linkrouting.LinkRoutingRules
import dev.sk2andy.materialbrowser.browser.integration.linkrouting.LinkRoutingTarget
import dev.sk2andy.materialbrowser.browser.integration.linkrouting.LinkRoutingTargetKind
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal class LinkRoutingStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, FILE_NAME))

    @Synchronized
    fun load(): List<LinkRoutingRule> {
        if (file.baseFile.length() > MAX_JSON_BYTES) return emptyList()
        val bytes = runCatching { file.openRead().use { it.readNBytes(MAX_JSON_BYTES + 1) } }
            .getOrNull() ?: return emptyList()
        if (bytes.size > MAX_JSON_BYTES) return emptyList()
        return runCatching {
            val root = JSONObject(bytes.toString(Charsets.UTF_8))
            if (root.getInt("version") != 1) return@runCatching emptyList()
            val array = root.getJSONArray("rules")
            buildList {
                for (index in 0 until minOf(array.length(), LinkRoutingRules.MAX_RULES * 2)) {
                    val item = array.optJSONObject(index) ?: continue
                    readRule(item)?.let(::add)
                }
            }.let(LinkRoutingRules::bounded)
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun save(rules: List<LinkRoutingRule>) {
        val array = JSONArray()
        LinkRoutingRules.bounded(rules).forEach { rule ->
            array.put(
                JSONObject()
                    .put("id", rule.id)
                    .put("domain", rule.domain)
                    .put("includeSubdomains", rule.includeSubdomains)
                    .put("target", rule.target.kind.wireValue)
                    .put("targetId", rule.target.id ?: JSONObject.NULL)
                    .put("enabled", rule.enabled),
            )
        }
        val bytes = JSONObject().put("version", 1).put("rules", array).toString().toByteArray(Charsets.UTF_8)
        check(bytes.size <= MAX_JSON_BYTES)
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (error: Throwable) {
            file.failWrite(stream)
            throw error
        }
    }

    private fun readRule(item: JSONObject): LinkRoutingRule? = runCatching {
        val kind = LinkRoutingTargetKind.entries.firstOrNull { it.wireValue == item.getString("target") }
            ?: return@runCatching null
        LinkRoutingRule(
            id = item.getString("id"),
            domain = item.getString("domain"),
            includeSubdomains = item.optBoolean("includeSubdomains", false),
            target = LinkRoutingTarget(kind, item.optString("targetId").takeUnless { it.isEmpty() || it == "null" }),
            enabled = item.optBoolean("enabled", true),
        ).let(LinkRoutingRules::sanitize)
    }.getOrNull()

    companion object {
        const val FILE_NAME = "link_routing_v1.json"
        private const val MAX_JSON_BYTES = 64 * 1024
    }
}
