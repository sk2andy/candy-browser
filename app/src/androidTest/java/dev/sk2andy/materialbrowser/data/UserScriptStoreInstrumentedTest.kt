package dev.sk2andy.materialbrowser.data

import android.util.AtomicFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.userscript.UserScript
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptParseResult
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptParser
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile

@RunWith(AndroidJUnit4::class)
class UserScriptStoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun atomicallyRoundTripsCanonicalLocalScripts() {
        val store = UserScriptStore(context)
        store.clear()
        val scripts = listOf(
            script(id = "early", runAt = "document-start", enabled = true),
            script(id = "late", runAt = "document-end", enabled = false),
        )

        assertTrue(store.save(scripts))
        assertEquals(scripts, store.load())
        store.clear()
    }

    @Test
    fun atomicallyRoundTripsResolvedDependencies() {
        val store = UserScriptStore(context)
        store.clear()
        val source = """
            // ==UserScript==
            // @name Dependency
            // @match https://example.com/*
            // @require https://cdn.example/library.js
            // @resource icon https://cdn.example/icon.png
            // @grant GM_getResourceURL
            // ==/UserScript==
            window.main = true;
        """.trimIndent()
        val parsed = (UserScriptParser.parse("dependency", source) as UserScriptParseResult.Accepted).script
        val resolved = parsed.copy(
            requires = parsed.requires.map { it.copy(source = "window.library = true;") },
            resources = parsed.resources.map {
                it.copy(encodedContent = "AAEC", mimeType = "image/png")
            },
        )

        assertTrue(store.save(listOf(resolved)))
        assertEquals(listOf(resolved), store.load())
        store.clear()
    }

    @Test
    fun loadsLegacySnapshotWithoutDependencies() {
        val store = UserScriptStore(context)
        store.clear()
        val legacy = script(id = "legacy", runAt = "document-end", enabled = true)
        val target = File(context.noBackupFilesDir, UserScriptStore.FILE_NAME)
        target.writeText(
            JSONObject()
                .put("version", 1)
                .put(
                    "scripts",
                    JSONArray().put(
                        JSONObject()
                            .put("id", legacy.id)
                            .put("source", legacy.source)
                            .put("enabled", legacy.enabled)
                            .put("updatedAtMillis", legacy.updatedAtMillis),
                    ),
                ).toString(),
        )

        assertEquals(listOf(legacy), store.load())
        store.clear()
    }

    @Test
    fun legacyDependencyEntryCannotDeleteOtherStoredScripts() {
        val store = UserScriptStore(context)
        store.clear()
        val stable = script(id = "stable", runAt = "document-end", enabled = true)
        val unresolvedSource = """
            // ==UserScript==
            // @name Legacy unresolved
            // @match https://example.com/*
            // @require https://raw.githubusercontent.com/example/library.js
            // ==/UserScript==
            window.main = true;
        """.trimIndent()
        val target = File(context.noBackupFilesDir, UserScriptStore.FILE_NAME)
        target.writeText(
            JSONObject()
                .put("version", 1)
                .put(
                    "scripts",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("id", "unresolved")
                                .put("source", unresolvedSource)
                                .put("enabled", true)
                                .put("updatedAtMillis", 0L),
                        )
                        .put(
                            JSONObject()
                                .put("id", stable.id)
                                .put("source", stable.source)
                                .put("enabled", stable.enabled)
                                .put("updatedAtMillis", stable.updatedAtMillis),
                        ),
                ).toString(),
        )

        assertEquals(listOf(stable), store.load())
        store.clear()
    }

    @Test
    fun rejectsTamperedDependencyPayload() {
        val store = UserScriptStore(context)
        store.clear()
        val digest = "8f434346648f6b96df89dda901c5176b10a6d83961dd3c1ac88b59b2dc327aa4"
        val source = """
            // ==UserScript==
            // @name Integrity
            // @match https://example.com/*
            // @require https://cdn.example/library.js#sha256=$digest
            // ==/UserScript==
            window.main = true;
        """.trimIndent()
        val parsed = (UserScriptParser.parse("integrity", source) as UserScriptParseResult.Accepted).script
        val resolved = parsed.copy(requires = parsed.requires.map { it.copy(source = "hi") })
        assertTrue(store.save(listOf(resolved)))
        val target = File(context.noBackupFilesDir, UserScriptStore.FILE_NAME)
        val root = JSONObject(target.readText())
        root.getJSONArray("scripts").getJSONObject(0)
            .getJSONArray("requires").getJSONObject(0)
            .put("source", "tampered")
        target.writeText(root.toString())

        assertTrue(store.load().isEmpty())
        assertFalse(target.exists())
    }

    @Test
    fun recoversLastCommittedSnapshotAfterInterruptedWrite() {
        val store = UserScriptStore(context)
        store.clear()
        val scripts = listOf(script(id = "stable", runAt = "document-end", enabled = true))
        assertTrue(store.save(scripts))
        val target = File(context.noBackupFilesDir, UserScriptStore.FILE_NAME)
        AtomicFile(target).startWrite().also { output ->
            output.write("partial".toByteArray())
            output.close()
        }

        assertEquals(scripts, store.load())
        store.clear()
    }

    @Test
    fun rejectsNonCanonicalSaveAndDeletesCorruptPersistedState() {
        val store = UserScriptStore(context)
        store.clear()
        val canonical = script(id = "local", runAt = "document-end", enabled = true)
        assertFalse(store.save(listOf(canonical.copy(name = "forged"))))

        val target = File(context.noBackupFilesDir, UserScriptStore.FILE_NAME)
        target.writeText(
            JSONObject()
                .put("version", UserScriptStore.FORMAT_VERSION)
                .put(
                    "scripts",
                    JSONArray().put(
                        JSONObject()
                            .put("id", "remote")
                            .put(
                                "source",
                                """
                                    // ==UserScript==
                                    // @name Remote
                                    // @match https://example.com/*
                                    // @require https://example.com/x.js
                                    // ==/UserScript==
                                    window.remote = true;
                                """.trimIndent(),
                            )
                            .put("enabled", true)
                            .put("updatedAtMillis", 0L),
                    ),
                ).toString(),
        )

        assertTrue(store.load().isEmpty())
        assertFalse(target.exists())
    }

    @Test
    fun deletesOversizeStateWithoutReadingIt() {
        val store = UserScriptStore(context)
        store.clear()
        val target = File(context.noBackupFilesDir, UserScriptStore.FILE_NAME)
        RandomAccessFile(target, "rw").use { file ->
            file.setLength(UserScriptStore.MAX_FILE_BYTES + 1L)
        }

        assertTrue(store.load().isEmpty())
        assertFalse(target.exists())
    }

    @Test
    fun rejectsEnabledCollectionBeyondRuntimeBudget() {
        val store = UserScriptStore(context)
        store.clear()
        val body = "window.large = true;\n" + "x".repeat(240 * 1_024)
        val scripts = List(9) { index ->
            val source = source("large-$index", "document-start") + "\n$body"
            (UserScriptParser.parse("large-$index", source) as UserScriptParseResult.Accepted).script
        }

        assertFalse(store.save(scripts))
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun rejectsDisabledCollectionBeyondStorageBudget() {
        val store = UserScriptStore(context)
        store.clear()
        val body = "window.stored = true;\n" + "x".repeat(240 * 1_024)
        val scripts = List(18) { index ->
            val source = source("stored-$index", "document-end") + "\n$body"
            (UserScriptParser.parse("stored-$index", source, enabled = false) as
                UserScriptParseResult.Accepted).script
        }

        assertFalse(store.save(scripts))
        assertTrue(store.load().isEmpty())
    }

    private fun script(id: String, runAt: String, enabled: Boolean): UserScript =
        (UserScriptParser.parse(id, source(id, runAt), enabled) as UserScriptParseResult.Accepted).script

    private fun source(id: String, runAt: String): String = """
        // ==UserScript==
        // @name Script $id
        // @match https://*.example.com/*
        // @run-at $runAt
        // @grant none
        // ==/UserScript==
        window.candy = ${JSONObject.quote(id)};
    """.trimIndent()
}
