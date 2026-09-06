package dev.sk2andy.materialbrowser.capsule

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CapsuleIconPackRepositoryInstrumentedTest {
    @Test
    fun discoversCatalogAndRendersDrawableFromInstalledTestPack() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val repository = CapsuleIconPackRepository(instrumentation.targetContext)
        val testPackageName = instrumentation.context.packageName

        val pack = repository.discover().firstOrNull { it.packageName == testPackageName }
        assertNotNull(pack)
        val entries = repository.loadEntries(requireNotNull(pack))
        val entry = entries.single { it.drawableName == "icon_pack_fixture" }
        val icon = requireNotNull(repository.render(entry))

        assertEquals(CapsuleCustomIconProcessor.OUTPUT_SIZE, icon.width)
        assertEquals(CapsuleCustomIconProcessor.OUTPUT_SIZE, icon.height)
        assertEquals(Color.rgb(0x33, 0x66, 0x99), icon.getPixel(96, 96))
    }
}
