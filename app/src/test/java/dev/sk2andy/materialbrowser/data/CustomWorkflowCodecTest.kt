package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflow
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowBinding
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowConfiguration
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowIcon
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowPlacement
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowRules
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowStep
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CustomWorkflowCodecTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `definitions and all placement references round trip including deleted workflows`() {
        val configuration = configuration()

        assertEquals(configuration, CustomWorkflowCodec.decode(requireNotNull(CustomWorkflowCodec.encode(configuration))))
    }

    @Test
    fun `unknown prerequisite rejects whole workflow while preserving its inactive binding`() {
        val bytes = requireNotNull(CustomWorkflowCodec.encode(configuration()))
        val changed = bytes.toString(Charsets.UTF_8).replace("save_reader_offline", "future_step")

        val decoded = CustomWorkflowCodec.decode(changed.toByteArray())

        assertEquals(emptyList<CustomWorkflow>(), decoded.workflows)
        assertEquals(configuration().bindings, decoded.bindings)
        assertNull(CustomWorkflowRules.resolve(decoded, CustomWorkflowPlacement.LinkLongPress))
    }

    @Test
    fun `unknown versions malformed and oversized input fail closed`() {
        val empty = CustomWorkflowConfiguration()
        assertEquals(empty, CustomWorkflowCodec.decode("broken".toByteArray()))
        assertEquals(empty, CustomWorkflowCodec.decode("""{"version":2,"workflows":[],"bindings":[]}""".toByteArray()))
        assertEquals(empty, CustomWorkflowCodec.decode("""{"version":4294967297,"workflows":[],"bindings":[]}""".toByteArray()))
        assertEquals(empty, CustomWorkflowCodec.decode(ByteArray(CustomWorkflowCodec.MAX_JSON_BYTES + 1)))
    }

    @Test
    fun `encoding refuses invalid definitions instead of silently deleting them`() {
        val malformed = configuration().copy(workflows = listOf(configuration().workflows.single().copy(steps = emptyList())))

        assertNull(CustomWorkflowCodec.encode(malformed))
    }

    @Test
    fun `fractional and overflowing slot values cannot become active bindings`() {
        val json = JSONObject(requireNotNull(CustomWorkflowCodec.encode(configuration())).toString(Charsets.UTF_8))
        json.getJSONArray("bindings").getJSONObject(0).put("slot", 4294967296L)
        json.getJSONArray("bindings").getJSONObject(1).put("slot", 1.5)

        val decoded = CustomWorkflowCodec.decode(json.toString().toByteArray())

        assertEquals(configuration().bindings.drop(2), decoded.bindings)
    }

    @Test
    fun `unknown icon has safe fallback and runtime fields are never reencoded`() {
        val json = JSONObject(requireNotNull(CustomWorkflowCodec.encode(configuration())).toString(Charsets.UTF_8))
        json.getJSONArray("workflows").getJSONObject(0).put("icon", "future_icon")
        json.put("pageUrl", "https://private.example/secret")
        json.put("executionResults", "private contents")

        val decoded = CustomWorkflowCodec.decode(json.toString().toByteArray())
        val encoded = requireNotNull(CustomWorkflowCodec.encode(decoded)).toString(Charsets.UTF_8)

        assertEquals(CustomWorkflowIcon.Steps, decoded.workflows.single().icon)
        assertFalse(encoded.contains("private.example"))
        assertFalse(encoded.contains("executionResults"))
    }

    @Test
    fun `manual archive export and import preserve definitions bindings and empty slots`() {
        val source = temporaryFolder.newFolder("source").toPath()
        val configuration = configuration()
        val path = "files/${CustomWorkflowCodec.FILE_NAME}"
        Files.createDirectories(source.resolve("files"))
        Files.write(source.resolve(path), requireNotNull(CustomWorkflowCodec.encode(configuration)))
        val manifest = AppDataArchiveManifest(
            packageName = "dev.example.browser",
            appVersionName = "1.0",
            appVersionCode = 1,
            webViewVersion = null,
            sdkInt = 35,
            exportedAtEpochMillis = 1,
        )
        val archive = ByteArrayOutputStream().also { AppDataArchiveCodec.export(source, manifest, it) }.toByteArray()
        val restored = temporaryFolder.newFolder("restored").toPath()

        AppDataArchiveCodec.extract(ByteArrayInputStream(archive), restored)

        val result = CustomWorkflowCodec.decode(Files.readAllBytes(restored.resolve(path)))
        assertEquals(configuration, result)
        assertNotNull(CustomWorkflowRules.binding(result, CustomWorkflowPlacement.AddressBar, 2))
        assertNull(CustomWorkflowRules.resolve(result, CustomWorkflowPlacement.AddressBar, 2))
        assertEquals("save", CustomWorkflowRules.resolve(result, CustomWorkflowPlacement.LinkPeekSwipe)?.id)
    }

    private fun configuration() = CustomWorkflowConfiguration(
        workflows = listOf(
            CustomWorkflow(
                id = "save",
                name = "Für später sichern",
                icon = CustomWorkflowIcon.Download,
                steps = listOf(CustomWorkflowStep.SaveReaderOffline, CustomWorkflowStep.CloseTab),
                showInMenu = false,
            ),
        ),
        bindings = CustomWorkflowPlacement.entries.flatMap { placement ->
            List(placement.slotCount) { slot ->
                CustomWorkflowBinding(placement, slot, if (slot == 2) "deleted" else "save")
            }
        },
    )
}
