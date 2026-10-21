package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflow
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowBinding
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowConfiguration
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowIcon
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowPlacement
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowRules
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowStep
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal object CustomWorkflowCodec {
    const val FILE_NAME = "custom_workflows_v1.json"
    const val MAX_JSON_BYTES = 128 * 1024
    private const val VERSION = 1

    fun decode(bytes: ByteArray): CustomWorkflowConfiguration {
        if (bytes.size > MAX_JSON_BYTES) return CustomWorkflowConfiguration()
        return try {
            val root = JSONObject(bytes.toString(Charsets.UTF_8))
            if (root.opt("version") != VERSION) return CustomWorkflowConfiguration()
            val workflowItems = root.getJSONArray("workflows")
            val bindingItems = root.getJSONArray("bindings")
            val workflows = buildList {
                for (index in 0 until minOf(workflowItems.length(), CustomWorkflowRules.MAX_WORKFLOWS * 2)) {
                    val item = workflowItems.optJSONObject(index) ?: continue
                    readWorkflow(item)?.let(::add)
                }
            }
            val bindings = buildList {
                for (index in 0 until minOf(bindingItems.length(), CustomWorkflowRules.MAX_BINDINGS * 2)) {
                    val item = bindingItems.optJSONObject(index) ?: continue
                    readBinding(item)?.let(::add)
                }
            }
            CustomWorkflowRules.normalize(CustomWorkflowConfiguration(workflows, bindings))
        } catch (_: JSONException) {
            CustomWorkflowConfiguration()
        }
    }

    fun encode(configuration: CustomWorkflowConfiguration): ByteArray? {
        if (CustomWorkflowRules.normalize(configuration) != configuration) return null
        val workflows = JSONArray().apply {
            configuration.workflows.forEach { workflow ->
                put(
                    JSONObject()
                        .put("id", workflow.id)
                        .put("name", workflow.name)
                        .put("icon", workflow.icon.wireValue)
                        .put("steps", JSONArray(workflow.steps.map { it.wireValue }))
                        .put("showInMenu", workflow.showInMenu),
                )
            }
        }
        val bindings = JSONArray().apply {
            configuration.bindings.forEach { binding ->
                put(
                    JSONObject()
                        .put("placement", binding.placement.wireValue)
                        .put("slot", binding.slot)
                        .put("workflowId", binding.workflowId),
                )
            }
        }
        return JSONObject()
            .put("version", VERSION)
            .put("workflows", workflows)
            .put("bindings", bindings)
            .toString()
            .toByteArray(Charsets.UTF_8)
            .takeIf { it.size <= MAX_JSON_BYTES }
    }

    private fun readWorkflow(item: JSONObject): CustomWorkflow? {
        return try {
            val iconValue = item.getString("icon")
            val icon = CustomWorkflowIcon.entries.firstOrNull { it.wireValue == iconValue }
            val stepItems = item.getJSONArray("steps")
            if (stepItems.length() !in 1..CustomWorkflowRules.MAX_STEPS) {
                null
            } else {
                val steps = buildList {
                    for (index in 0 until stepItems.length()) {
                        val wireValue = stepItems.getString(index)
                        // Never remove an unknown prerequisite and leave a later close action executable.
                        val step = CustomWorkflowStep.entries.firstOrNull { it.wireValue == wireValue }
                            ?: return null
                        add(step)
                    }
                }
                CustomWorkflow(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    icon = icon ?: CustomWorkflowIcon.Steps,
                    steps = steps,
                    showInMenu = if (item.has("showInMenu")) item.getBoolean("showInMenu") else true,
                )
            }
        } catch (_: JSONException) {
            null
        }
    }

    private fun readBinding(item: JSONObject): CustomWorkflowBinding? {
        return try {
            val placementValue = item.getString("placement")
            val placement = CustomWorkflowPlacement.entries.firstOrNull { it.wireValue == placementValue }
                ?: return null
            val slot = item.opt("slot") as? Int ?: return null
            CustomWorkflowBinding(placement, slot, item.getString("workflowId"))
        } catch (_: JSONException) {
            null
        }
    }
}
