package dev.sk2andy.materialbrowser.data

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.ProfileWallpaperTarget
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileWallpaperStoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = ProfileWallpaperStore(context)
    private val profileIds = mutableSetOf<String>()

    @After
    fun tearDown() {
        profileIds.forEach(store::delete)
    }

    @Test
    fun wallpapersRoundTripAndDeleteIndependently() {
        val profileId = UUID.randomUUID().toString().also(profileIds::add)
        val newTabBitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888)
        val switcherBitmap = Bitmap.createBitmap(48, 36, Bitmap.Config.ARGB_8888)

        assertTrue(store.save(profileId, ProfileWallpaperTarget.NewTab, newTabBitmap))
        assertTrue(store.save(profileId, ProfileWallpaperTarget.TabSwitcher, switcherBitmap))
        val restoredNewTab = requireNotNull(
            store.load(profileId, ProfileWallpaperTarget.NewTab),
        )
        val restoredSwitcher = requireNotNull(
            store.load(profileId, ProfileWallpaperTarget.TabSwitcher),
        )

        assertEquals(32, restoredNewTab.width)
        assertEquals(48, restoredSwitcher.width)
        store.delete(profileId, ProfileWallpaperTarget.NewTab)
        assertNull(store.load(profileId, ProfileWallpaperTarget.NewTab))
        assertEquals(48, store.load(profileId, ProfileWallpaperTarget.TabSwitcher)?.width)
        restoredNewTab.recycle()
        restoredSwitcher.recycle()
        newTabBitmap.recycle()
        switcherBitmap.recycle()
    }

    @Test
    fun cleanupKeepsOnlyKnownProfileWallpapers() {
        val kept = UUID.randomUUID().toString().also(profileIds::add)
        val removed = UUID.randomUUID().toString().also(profileIds::add)
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        assertTrue(store.save(kept, ProfileWallpaperTarget.NewTab, bitmap))
        assertTrue(store.save(kept, ProfileWallpaperTarget.TabSwitcher, bitmap))
        assertTrue(store.save(removed, ProfileWallpaperTarget.NewTab, bitmap))

        store.cleanup(setOf(kept to ProfileWallpaperTarget.NewTab))

        assertTrue(store.fileFor(kept, ProfileWallpaperTarget.NewTab)?.isFile == true)
        assertFalse(store.fileFor(kept, ProfileWallpaperTarget.TabSwitcher)?.exists() == true)
        assertFalse(store.fileFor(removed, ProfileWallpaperTarget.NewTab)?.exists() == true)
        bitmap.recycle()
    }

    @Test
    fun legacyWallpaperMigratesToBothTargets() {
        val profileId = UUID.randomUUID().toString().also(profileIds::add)
        val bitmap = Bitmap.createBitmap(18, 12, Bitmap.Config.ARGB_8888)
        val legacyFile = requireNotNull(store.legacyFileFor(profileId))
        legacyFile.parentFile?.mkdirs()
        legacyFile.outputStream().use { output ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 88, output))
        }

        store.migrateLegacy(setOf(profileId))

        assertEquals(18, store.load(profileId, ProfileWallpaperTarget.NewTab)?.width)
        assertEquals(18, store.load(profileId, ProfileWallpaperTarget.TabSwitcher)?.width)
        assertFalse(legacyFile.exists())
        bitmap.recycle()
    }

    @Test
    fun legacyAtomicBackupMigratesWhenBaseFileIsMissing() {
        val profileId = UUID.randomUUID().toString().also(profileIds::add)
        val bitmap = Bitmap.createBitmap(22, 14, Bitmap.Config.ARGB_8888)
        val legacyFile = requireNotNull(store.legacyFileFor(profileId))
        val backupFile = File("${legacyFile.path}.bak")
        backupFile.parentFile?.mkdirs()
        backupFile.outputStream().use { output ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 88, output))
        }

        store.migrateLegacy(setOf(profileId))

        assertEquals(22, store.load(profileId, ProfileWallpaperTarget.NewTab)?.width)
        assertEquals(22, store.load(profileId, ProfileWallpaperTarget.TabSwitcher)?.width)
        assertFalse(legacyFile.exists())
        assertFalse(backupFile.exists())
        bitmap.recycle()
    }

    @Test
    fun malformedProfileIdCannotEscapeWallpaperDirectory() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)

        assertTrue(store.save("../escape", ProfileWallpaperTarget.NewTab, bitmap))
        val target = requireNotNull(store.fileFor("../escape", ProfileWallpaperTarget.NewTab))
        assertEquals("profile_wallpapers", target.parentFile?.name)
        assertFalse(target.name.contains(".."))
        store.delete("../escape")
        bitmap.recycle()
    }
}
