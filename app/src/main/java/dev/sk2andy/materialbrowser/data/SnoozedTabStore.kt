package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.content.SharedPreferences
import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.DEFAULT_PROFILE_ID
import org.json.JSONArray
import org.json.JSONObject

class SnoozedTabStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        BrowserSessionStore.PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun load(): List<SnoozedTab> {
        val raw = preferences.getString(KEY_TABS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList<SnoozedTab> {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val id = item.optString("id").takeIf(String::isNotBlank) ?: continue
                    val wakeAtMillis = item.optLong("wakeAtMillis")
                    if (
                        wakeAtMillis <= 0L ||
                        item.optBoolean("isIncognito", false) ||
                        any { it.tab.id == id }
                    ) continue
                    add(
                        SnoozedTab(
                            tab = BrowserTab(
                                id = id,
                                lastAccessedAt = item.optLong("lastAccessedAt").coerceAtLeast(0L),
                                profileId = item.optString("profileId", DEFAULT_PROFILE_ID)
                                    .takeIf(String::isNotBlank)
                                    ?: DEFAULT_PROFILE_ID,
                                isIncognito = false,
                                isPinned = item.optBoolean("isPinned", false),
                                title = item.optString("title", ""),
                                url = item.optString("url", BLANK_URL),
                            ),
                            wakeAtMillis = wakeAtMillis,
                            createdAtMillis = item.optLong("createdAtMillis").coerceAtLeast(0L),
                        ),
                    )
                }
            }.sortedWith(compareBy<SnoozedTab>({ it.wakeAtMillis }, { it.tab.id }))
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun save(tabs: List<SnoozedTab>): Boolean = putTabs(preferences.edit(), tabs).commit()

    internal companion object {
        const val KEY_TABS = "snoozed_tabs"

        fun putTabs(
            editor: SharedPreferences.Editor,
            tabs: List<SnoozedTab>,
        ): SharedPreferences.Editor {
            val safeTabs = tabs.asSequence()
                .filterNot { it.tab.isIncognito }
                .distinctBy { it.tab.id }
                .sortedWith(compareBy<SnoozedTab>({ it.wakeAtMillis }, { it.tab.id }))
                .toList()
            val array = JSONArray()
            safeTabs.forEach { snoozed ->
                val tab = snoozed.tab
                array.put(
                    JSONObject()
                        .put("id", tab.id)
                        .put("lastAccessedAt", tab.lastAccessedAt)
                        .put("profileId", tab.profileId)
                        .put("isPinned", tab.isPinned)
                        .put("title", tab.title)
                        .put("url", tab.url)
                        .put("wakeAtMillis", snoozed.wakeAtMillis)
                        .put("createdAtMillis", snoozed.createdAtMillis),
                )
            }
            return editor.putString(KEY_TABS, array.toString())
        }
    }
}
