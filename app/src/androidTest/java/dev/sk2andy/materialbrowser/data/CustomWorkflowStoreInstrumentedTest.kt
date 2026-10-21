package dev.sk2andy.materialbrowser.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflow
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowBinding
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowConfiguration
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowIcon
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowPlacement
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowRules
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowStep
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
class CustomWorkflowStoreInstrumentedTest {
    private lateinit var directory: File
    private lateinit var file: File

    @Before
    fun createStoreDirectory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(context.cacheDir, "custom-workflow-test-${UUID.randomUUID()}")
        assertTrue(directory.mkdirs())
        file = File(directory, CustomWorkflowStore.FILE_NAME)
    }

    @After
    fun removeStoreDirectory() {
        directory.deleteRecursively()
    }

    @Test
    fun savedDefinitionsAndDeletedReferencesSurviveReopening() {
        val configuration = configuration()
        val store = CustomWorkflowStore(file)
        assertTrue(store.save(configuration))
        assertEquals(configuration, CustomWorkflowStore(file).load())

        val deleted = CustomWorkflowRules.delete(configuration, "save")
        assertTrue(store.save(deleted))
        val restored = CustomWorkflowStore(file).load()

        assertEquals(deleted, restored)
        assertNotNull(CustomWorkflowRules.binding(restored, CustomWorkflowPlacement.LinkLongPress))
        assertNull(CustomWorkflowRules.resolve(restored, CustomWorkflowPlacement.LinkLongPress))
    }

    @Test
    fun invalidSaveLeavesPreviouslySavedDefinitionIntact() {
        val store = CustomWorkflowStore(file)
        val configuration = configuration()
        assertTrue(store.save(configuration))

        assertFalse(store.save(configuration.copy(workflows = listOf(configuration.workflows.single().copy(steps = emptyList())))))

        assertEquals(configuration, store.load())
    }

    @Test
    fun corruptOversizedAndMissingStoresLoadEmpty() {
        val store = CustomWorkflowStore(file)
        assertEquals(CustomWorkflowConfiguration(), store.load())
        file.writeText("not JSON")
        assertEquals(CustomWorkflowConfiguration(), store.load())
        file.writeBytes(ByteArray(CustomWorkflowCodec.MAX_JSON_BYTES + 1))
        assertEquals(CustomWorkflowConfiguration(), store.load())
    }

    @Test
    fun interruptedAtomicWriteRestoresPreviousSnapshot() {
        val configuration = configuration()
        val store = CustomWorkflowStore(file)
        assertTrue(store.save(configuration))
        assertTrue(file.renameTo(File(file.path + ".bak")))
        file.writeText("partial write")

        assertEquals(configuration, CustomWorkflowStore(file).load())
    }

    @Test
    fun unwritableStoreReportsFailure() {
        val parentFile = File(directory, "not-a-directory").apply { writeText("keep") }
        val store = CustomWorkflowStore(File(parentFile, CustomWorkflowStore.FILE_NAME))

        assertFalse(store.save(configuration()))
        assertEquals("keep", parentFile.readText())
    }

    private fun configuration() = CustomWorkflowConfiguration(
        workflows = listOf(
            CustomWorkflow(
                id = "save",
                name = "Für später sichern",
                icon = CustomWorkflowIcon.Download,
                steps = listOf(CustomWorkflowStep.SaveReaderOffline, CustomWorkflowStep.CloseTab),
            ),
        ),
        bindings = listOf(
            CustomWorkflowBinding(CustomWorkflowPlacement.LinkLongPress, 0, "save"),
            CustomWorkflowBinding(CustomWorkflowPlacement.MenuFooter, 4, "already-deleted"),
        ),
    )
}
