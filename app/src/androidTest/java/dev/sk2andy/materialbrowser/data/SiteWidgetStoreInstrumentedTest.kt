package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.ProfileLockTrigger
import dev.sk2andy.materialbrowser.browser.ProfileProtection
import dev.sk2andy.materialbrowser.browser.sitewidget.SiteWidgetCrop
import dev.sk2andy.materialbrowser.browser.sitewidget.SiteWidgetCropRules
import dev.sk2andy.materialbrowser.browser.sitewidget.SiteWidgetFormat
import dev.sk2andy.materialbrowser.browser.sitewidget.SiteWidgetGridSizingRules
import dev.sk2andy.materialbrowser.browser.sitewidget.SiteWidgetSnapshot
import dev.sk2andy.materialbrowser.browser.sitewidget.SiteWidgetSource
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SiteWidgetStoreInstrumentedTest {
    private val baseContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val testId = UUID.randomUUID().toString()
    private val root = File(baseContext.cacheDir, "site-widget-test-$testId")
    private val context = object : ContextWrapper(baseContext) {
        override fun getApplicationContext(): Context = this

        override fun getNoBackupFilesDir(): File = root

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            baseContext.getSharedPreferences("site-widget-test-$testId-$name", mode)
    }
    private val profiles = BrowserSessionStore(context)
    private val store = SiteWidgetStore(context)
    private val profile = BrowserProfile(id = "widget-profile", emoji = "🍬")
    private lateinit var bitmap: Bitmap

    @Before
    fun setUp() {
        profiles.saveProfiles(listOf(profile), profile.id)
        bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
    }

    @After
    fun tearDown() {
        bitmap.recycle()
        root.deleteRecursively()
        File(baseContext.applicationInfo.dataDir, "shared_prefs").listFiles()
            ?.filter { it.name.startsWith("site-widget-test-$testId-") }
            ?.forEach(File::delete)
    }

    @Test
    fun learnedLauncherGeometryPersistsForNextWidgetGrid() {
        val launcherPackage = "dev.example.launcher"
        val ratio = requireNotNull(
            SiteWidgetGridSizingRules.cellAspectRatio(
                minWidthDp = 320,
                minHeightDp = 180,
                maxWidthDp = 480,
                maxHeightDp = 360,
                isLandscape = false,
                format = SiteWidgetFormat.FourByThree,
            ),
        )
        assertTrue(store.saveLauncherCellAspectRatio(launcherPackage, ratio))

        val sizingStore = SiteWidgetGridSizingStore(context)
        val learnedRatio = requireNotNull(sizingStore.cellAspectRatio(launcherPackage))
        val nextCrop = requireNotNull(
            SiteWidgetCropRules.initialCrop(SiteWidgetFormat(3, 2), 400, 800, learnedRatio),
        )
        assertEquals(1f, nextCrop.width, 0.0001f)
        assertEquals(0.5f, nextCrop.height, 0.0001f)
        assertNull(sizingStore.cellAspectRatio("dev.other.launcher"))
        assertFalse(store.saveLauncherCellAspectRatio(launcherPackage, Float.NaN))
        assertEquals(learnedRatio, sizingStore.cellAspectRatio(launcherPackage))
    }

    @Test
    fun refreshRoundTripsGeometryAndCannotOverwriteReplacedOrProtectedBinding() {
        val original = snapshot().let {
            it.copy(
                source = it.source.copy(
                    engineId = "system_webview",
                    viewportWidthPx = 400,
                    viewportHeightPx = 700,
                    scrollY = 240,
                    desktopMode = true,
                ),
                refreshIntervalMinutes = 30,
            )
        }
        assertTrue(store.save(original, bitmap))
        assertTrue(store.bind(42, original.id))
        assertEquals(original, SiteWidgetStore(context).snapshotForWidget(42))
        val refreshed = original.copy(id = UUID.randomUUID().toString())
        assertTrue(store.replaceIfCurrent(42, original.id, refreshed, bitmap))
        assertFalse(store.replaceIfCurrent(42, original.id, original, bitmap))
        assertEquals(refreshed, store.snapshotForWidget(42))
        assertNull(store.load(original.id))
        profiles.saveProfiles(
            listOf(profile.copy(protection = ProfileProtection(ProfileLockTrigger.AppClosed))),
            profile.id,
        )
        assertFalse(store.replaceIfCurrent(42, refreshed.id, original, bitmap))
    }

    @Test
    fun refreshAtSnapshotCapacityReplacesLastBindingWithoutIncreasingRetention() {
        val snapshots = List(32) { snapshot() }
        snapshots.forEachIndexed { index, snapshot ->
            assertTrue(store.save(snapshot, bitmap))
            assertTrue(store.bind(index + 1, snapshot.id))
        }
        val original = snapshots.first()
        val refreshed = original.copy(id = UUID.randomUUID().toString())

        assertTrue(store.replaceIfCurrent(1, original.id, refreshed, bitmap))
        assertEquals(refreshed, store.snapshotForWidget(1))
        assertNull(store.load(original.id))
        assertFalse(store.save(snapshot(), bitmap))
        assertEquals(32, store.boundSnapshots().size)
    }

    @Test
    fun refreshAtCapacityCannotReserveSlotWhileAnotherWidgetStillUsesPreviousCapture() {
        val snapshots = List(32) { snapshot() }
        snapshots.forEachIndexed { index, snapshot ->
            assertTrue(store.save(snapshot, bitmap))
            assertTrue(store.bind(index + 1, snapshot.id))
        }
        val original = snapshots.first()
        assertTrue(store.bind(40, original.id))

        assertFalse(store.replaceIfCurrent(1, original.id, original.copy(id = UUID.randomUUID().toString()), bitmap))
        assertEquals(original, store.snapshotForWidget(1))
        assertEquals(original, store.snapshotForWidget(40))
    }

    @Test
    fun persistsCaptureAndWidgetBindingAcrossStoreInstances() {
        val snapshot = snapshot()
        assertTrue(store.save(snapshot, bitmap))
        assertTrue(store.bind(42, snapshot.id))

        val restoredStore = SiteWidgetStore(context)
        assertEquals(snapshot, restoredStore.snapshotForWidget(42))
        val restoredBitmap = restoredStore.loadBitmap(snapshot.id)
        assertNotNull(restoredBitmap)
        assertEquals(32, restoredBitmap?.width)
        assertEquals(Color.MAGENTA, restoredBitmap?.getPixel(16, 12))
        restoredBitmap?.recycle()
        assertFalse(File(context.filesDir, "site_widget_snapshots_v1").exists())
    }

    @Test
    fun refusesPrivateMissingAndProtectedProfilesAtStorageBoundary() {
        val snapshot = snapshot()
        assertFalse(store.save(snapshot.copy(source = snapshot.source.copy(isPrivate = true)), bitmap))
        assertFalse(store.save(snapshot.copy(source = snapshot.source.copy(profileId = "missing")), bitmap))
        profiles.saveProfiles(
            listOf(profile.copy(protection = ProfileProtection(ProfileLockTrigger.AppBackgrounded))),
            profile.id,
        )
        assertFalse(store.save(snapshot, bitmap))
    }

    @Test
    fun protectionChangeImmediatelyHidesPreviouslySavedCapture() {
        val snapshot = snapshot()
        assertTrue(store.save(snapshot, bitmap))
        assertTrue(store.bind(42, snapshot.id))
        profiles.saveProfiles(
            listOf(profile.copy(protection = ProfileProtection(ProfileLockTrigger.AppClosed))),
            profile.id,
        )

        assertNull(store.load(snapshot.id))
        assertNull(store.loadBitmap(snapshot.id))
        assertNull(store.snapshotForWidget(42))
        store.invalidateProfiles(setOf(profile.id))
        assertFalse(File(root, "site_widget_snapshots_v1/${snapshot.id}.png").exists())
    }

    @Test
    fun widgetDeletionPreservesCaptureUntilLastBindingRemoved() {
        val snapshot = snapshot()
        assertTrue(store.save(snapshot, bitmap))
        assertTrue(store.bind(42, snapshot.id))
        assertTrue(store.bind(43, snapshot.id))
        store.deleteWidget(42)
        assertEquals(snapshot, store.snapshotForWidget(43))
        store.deleteWidget(43)
        assertNull(store.load(snapshot.id))
    }

    @Test
    fun replacingBindingDeletesPreviousCaptureOnlyAfterLastReferenceMoves() {
        val previous = snapshot()
        val replacement = snapshot()
        assertTrue(store.save(previous, bitmap))
        assertTrue(store.bind(42, previous.id))
        assertTrue(store.bind(43, previous.id))
        assertTrue(store.save(replacement, bitmap))

        assertTrue(store.bind(42, replacement.id))
        assertEquals(previous, store.load(previous.id))
        assertTrue(store.bind(43, replacement.id))
        assertNull(store.load(previous.id))
        assertEquals(replacement, store.snapshotForWidget(42))
        assertEquals(replacement, store.snapshotForWidget(43))
    }

    @Test
    fun savingPrunesExpiredDraftsWhileKeepingBoundAndRecentCaptures() {
        val expired = snapshot().copy(createdAtMillis = 1_000L)
        val bound = snapshot().copy(createdAtMillis = 1_000L)
        val recent = snapshot()
        assertTrue(store.save(bound, bitmap))
        assertTrue(store.bind(42, bound.id))
        assertTrue(store.save(expired, bitmap))
        assertTrue(store.save(recent, bitmap))

        assertNull(store.load(expired.id))
        assertEquals(bound, store.snapshotForWidget(42))
        assertEquals(recent, store.load(recent.id))
    }

    @Test
    fun rejectsCorruptAndOversizedImageWithoutDecodingUnboundedPixels() {
        val snapshot = snapshot()
        assertTrue(store.save(snapshot, bitmap))
        val image = File(root, "site_widget_snapshots_v1/${snapshot.id}.png")
        image.writeText("broken image")
        assertNull(store.loadBitmap(snapshot.id))
        val oversized = Bitmap.createBitmap(769, 1, Bitmap.Config.ARGB_8888)
        assertFalse(store.save(snapshot(), oversized))
        oversized.recycle()
        assertFalse(store.bind(0, snapshot.id))
        assertNull(store.load("../${snapshot.id}"))
    }

    private fun snapshot() = SiteWidgetSnapshot(
        id = UUID.randomUUID().toString(),
        source = SiteWidgetSource("tab", profile.id, "https://example.com/status", "Status", 3),
        format = SiteWidgetFormat.FourByThree,
        crop = SiteWidgetCrop(0f, 0f, 1f, 0.5f),
        createdAtMillis = System.currentTimeMillis(),
    )
}
