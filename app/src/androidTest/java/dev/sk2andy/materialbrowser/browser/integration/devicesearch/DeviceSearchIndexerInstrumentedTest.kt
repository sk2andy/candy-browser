package dev.sk2andy.materialbrowser.browser.integration.devicesearch

import android.app.appsearch.AppSearchManager
import android.app.appsearch.AppSearchSession
import android.app.appsearch.GenericDocument
import android.app.appsearch.SearchSpec
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.ProfileLockTrigger
import dev.sk2andy.materialbrowser.browser.ProfileProtection
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.FavoriteEntry
import dev.sk2andy.materialbrowser.reader.ReaderLibraryStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DeviceSearchIndexerInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val direct = Executor { it.run() }
    private val profile = BrowserProfile("candy", "🍬")
    private val favorite = FavoriteEntry("https://example.com/shared", "Shared bookmark", 1L)
    private val itemId = "f:${favorite.id}"

    @Before
    fun setUp() {
        clearPreferences()
        BrowserSessionStore(context).saveProfiles(listOf(profile), profile.id)
        BrowserSessionStore(context).saveFavorites(listOf(favorite))
    }

    @After
    fun tearDown() {
        DeviceSearchStore(context).save(DeviceSearchSettings())
        refreshIndex()
        clearPreferences()
    }

    @Test
    fun explicitSelectionIndexesCompatiblePageAndDisablingRevokesSystemVisibility() {
        refreshIndex()
        assertTrue(documents().isEmpty())
        DeviceSearchStore(context).save(DeviceSearchSettings(enabled = true, selectedIds = setOf(itemId)))
        refreshIndex()

        val document = documents().single()
        assertEquals(itemId, document.id)
        assertEquals("Shared bookmark", document.getPropertyString("name"))
        assertEquals(favorite.url, document.getPropertyString("description"))
        val action = requireNotNull(document.getPropertyDocument("potentialActions"))
        val openIntent = Intent.parseUri(action.getPropertyString("uri"), Intent.URI_INTENT_SCHEME)
        assertEquals(itemId, DeviceSearchContract.itemId(openIntent))
        session().use { session ->
            val hidden = CompletableFuture<Set<String>>()
            session.getSchema(direct) { result -> hidden.complete(requireNotNull(result.resultValue).schemaTypesNotDisplayedBySystem) }
            assertFalse(DeviceSearchIndexer.PAGE_SCHEMA in hidden.get(10L, TimeUnit.SECONDS))
        }

        DeviceSearchStore(context).save(DeviceSearchSettings(enabled = false, selectedIds = setOf(itemId)))
        refreshIndex()
        assertTrue(documents().isEmpty())
        session().use { session ->
            val hidden = CompletableFuture<Set<String>>()
            session.getSchema(direct) { result -> hidden.complete(requireNotNull(result.resultValue).schemaTypesNotDisplayedBySystem) }
            assertTrue(DeviceSearchIndexer.PAGE_SCHEMA in hidden.get(10L, TimeUnit.SECONDS))
        }
    }

    @Test
    fun protectedOwnerIsRemovedAndItsConsentCannotSilentlyReturn() {
        DeviceSearchStore(context).save(DeviceSearchSettings(enabled = true, selectedIds = setOf(itemId)))
        refreshIndex()
        assertEquals(1, documents().size)

        BrowserSessionStore(context).saveProfiles(
            listOf(profile.copy(protection = ProfileProtection(ProfileLockTrigger.AppBackgrounded))),
            profile.id,
        )
        refreshIndex()

        assertTrue(documents().isEmpty())
        assertTrue(DeviceSearchStore(context).load().selectedIds.isEmpty())
    }

    private fun refreshIndex() {
        repeat(3) {
            val complete = CompletableFuture<Boolean>()
            DeviceSearchIndexer.refresh(context, complete::complete)
            if (complete.get(15L, TimeUnit.SECONDS)) return
        }
        throw AssertionError("AppSearch index did not settle")
    }

    private fun documents(): List<GenericDocument> = session().use { session ->
        session.search("", SearchSpec.Builder().addFilterNamespaces(DeviceSearchIndexer.NAMESPACE).build()).use { search ->
            val page = CompletableFuture<List<GenericDocument>>()
            search.getNextPage(direct) { result -> page.complete(requireNotNull(result.resultValue).map { it.genericDocument }) }
            page.get(10L, TimeUnit.SECONDS)
        }
    }

    private fun session(): AppSearchSession {
        val opened = CompletableFuture<AppSearchSession>()
        context.getSystemService(AppSearchManager::class.java).createSearchSession(
            AppSearchManager.SearchContext.Builder(DeviceSearchIndexer.DATABASE_NAME).build(),
            direct,
        ) { result -> opened.complete(result.resultValue) }
        return opened.get(10L, TimeUnit.SECONDS)
    }

    private fun clearPreferences() {
        listOf(BrowserSessionStore.PREFERENCES_NAME, DeviceSearchStore.PREFERENCES_NAME).forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        ReaderLibraryStore(context).clear()
    }
}
