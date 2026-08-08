package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.BrowserTab
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SnoozedTabStoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences by lazy {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun tabMetadataRoundTripsAcrossStoreRecreation() {
        val store = SnoozedTabStore(context)
        val snoozed = SnoozedTab(
            tab = BrowserTab(
                id = "persisted",
                lastAccessedAt = 44L,
                profileId = "work",
                isPinned = true,
                title = "Candy",
                url = "https://example.com",
            ),
            wakeAtMillis = 88L,
            createdAtMillis = 22L,
        )

        store.save(listOf(snoozed))
        val restored = SnoozedTabStore(context).load().single()

        assertEquals(snoozed, restored)
    }

    @Test
    fun rescheduleAndDeletePersist() {
        val original = SnoozedTab(BrowserTab("tab", 1L), 10L, 2L)
        val store = SnoozedTabStore(context)
        store.save(listOf(original))

        store.save(listOf(original.copy(wakeAtMillis = 20L)))
        assertEquals(20L, SnoozedTabStore(context).load().single().wakeAtMillis)

        store.save(emptyList())
        assertEquals(emptyList<SnoozedTab>(), SnoozedTabStore(context).load())
    }

    @Test
    fun privateTabsNeverReachPersistentJson() {
        val privateTab = SnoozedTab(BrowserTab("private", 1L, isIncognito = true), 10L, 2L)

        SnoozedTabStore(context).save(listOf(privateTab))

        assertEquals(emptyList<SnoozedTab>(), SnoozedTabStore(context).load())
        assertFalse(
            preferences.getString(SnoozedTabStore.KEY_TABS, "").orEmpty().contains("private"),
        )
    }

    @Test
    fun malformedPersistenceFailsClosed() {
        preferences.edit().putString(SnoozedTabStore.KEY_TABS, "not-json").commit()

        assertEquals(emptyList<SnoozedTab>(), SnoozedTabStore(context).load())
    }

    @Test
    fun legacyPrivateRecordIsDiscardedOnLoad() {
        preferences.edit()
            .putString(
                SnoozedTabStore.KEY_TABS,
                """[{"id":"private","isIncognito":true,"wakeAtMillis":100}]""",
            )
            .commit()

        assertEquals(emptyList<SnoozedTab>(), SnoozedTabStore(context).load())
    }

    @Test
    fun activeAndSnoozedMoveUsesOnePersistentSnapshot() {
        val sessionStore = BrowserSessionStore(context)
        val pending = SnoozedTab(BrowserTab("due", 1L), 10L, 2L)
        assertTrue(
            sessionStore.saveTabsAndSnoozedImmediately(
                tabs = listOf(BrowserTab("active", 1L)),
                selectedTabId = "active",
                snoozedTabs = listOf(pending),
            ),
        )

        assertTrue(
            sessionStore.saveTabsAndSnoozedImmediately(
                tabs = listOf(BrowserTab("active", 1L), pending.tab.copy(lastAccessedAt = 10L)),
                selectedTabId = "active",
                snoozedTabs = emptyList(),
            ),
        )

        assertEquals(listOf("active", "due"), BrowserSessionStore(context).loadTabs().first.map { it.id })
        assertEquals(emptyList<SnoozedTab>(), SnoozedTabStore(context).load())
    }

    @Test
    fun failedAtomicSnapshotRestoresSharedPreferencesMemoryState() {
        val preferences = FailingSharedPreferences()
        val sessionStore = BrowserSessionStore(preferences)
        val pending = SnoozedTab(BrowserTab("due", 1L), 10L, 2L)
        assertTrue(
            sessionStore.saveTabsAndSnoozedImmediately(
                tabs = listOf(BrowserTab("active", 1L)),
                selectedTabId = "active",
                snoozedTabs = listOf(pending),
            ),
        )
        preferences.failNextCommit = true

        assertFalse(
            sessionStore.saveTabsAndSnoozedImmediately(
                tabs = listOf(BrowserTab("active", 1L), pending.tab),
                selectedTabId = "active",
                snoozedTabs = emptyList(),
            ),
        )

        assertEquals(listOf("active"), sessionStore.loadTabs().first.map { it.id })
        val rawSnoozed = preferences.getString(SnoozedTabStore.KEY_TABS, null).orEmpty()
        assertTrue(rawSnoozed.contains("due"))
    }
}

private class FailingSharedPreferences : SharedPreferences {
    private val values = linkedMapOf<String, Any?>()
    var failNextCommit = false

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String, defValue: String?): String? =
        values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<String>)?.toMutableSet() ?: defValues

    override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        values[key] as? Boolean ?: defValue

    override fun contains(key: String): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val updates = linkedMapOf<String, Any?>()
        private val removals = linkedSetOf<String>()
        private var clear = false

        override fun putString(key: String, value: String?) = update(key, value)
        override fun putStringSet(key: String, values: MutableSet<String>?) =
            update(key, values?.toSet())

        override fun putInt(key: String, value: Int) = update(key, value)
        override fun putLong(key: String, value: Long) = update(key, value)
        override fun putFloat(key: String, value: Float) = update(key, value)
        override fun putBoolean(key: String, value: Boolean) = update(key, value)
        override fun remove(key: String): SharedPreferences.Editor = apply {
            removals += key
            updates -= key
        }

        override fun clear(): SharedPreferences.Editor = apply { clear = true }

        override fun commit(): Boolean {
            applyChanges()
            return if (failNextCommit) {
                failNextCommit = false
                false
            } else {
                true
            }
        }

        override fun apply() = applyChanges()

        private fun update(key: String, value: Any?): SharedPreferences.Editor = apply {
            updates[key] = value
            removals -= key
        }

        private fun applyChanges() {
            if (clear) values.clear()
            removals.forEach(values::remove)
            updates.forEach { (key, value) ->
                if (value == null) values.remove(key) else values[key] = value
            }
        }
    }
}
