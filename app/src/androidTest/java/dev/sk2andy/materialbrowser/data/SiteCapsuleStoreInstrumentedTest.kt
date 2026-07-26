package dev.sk2andy.materialbrowser.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.capsule.CapsuleChromeMode
import dev.sk2andy.materialbrowser.capsule.CapsuleNavigationMode
import dev.sk2andy.materialbrowser.capsule.SiteCapsule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SiteCapsuleStoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = SiteCapsuleStore(context)
    private val storeFile = File(context.filesDir, "site_capsules_v1.json")

    @Before
    fun setUp() {
        storeFile.delete()
        File(storeFile.path + ".bak").delete()
    }

    @After
    fun tearDown() = setUp()

    @Test
    fun versionedCapsuleRoundTripsAcrossStoreInstances() {
        val capsule = SiteCapsule(
            id = "04a74ad8-7533-460c-bfbf-a135968940d5",
            name = "Mail",
            startUrl = "https://mail.example/inbox",
            profileId = "work",
            ownsDedicatedProfile = true,
            isolatedStorageRequested = true,
            navigationMode = CapsuleNavigationMode.SameRegistrableDomain,
            chromeMode = CapsuleChromeMode.NoControls,
            createdAtMillis = 10L,
            updatedAtMillis = 20L,
        )

        store.save(listOf(capsule))

        assertEquals(listOf(capsule), SiteCapsuleStore(context).load())
    }

    @Test
    fun corruptAndOversizedDataFailsClosed() {
        storeFile.writeText("not-json")
        assertTrue(store.load().isEmpty())

        storeFile.writeBytes(ByteArray(512 * 1024 + 1) { 'x'.code.toByte() })
        assertTrue(store.load().isEmpty())
    }
}
