package dev.sk2andy.materialbrowser.browser.workflow

internal enum class CustomWorkflowStep(val wireValue: String) {
    SaveReaderOffline("save_reader_offline"),
    AddFavorite("add_favorite"),
    PinTab("pin_tab"),
    CopyUrl("copy_url"),
    OpenReader("open_reader"),
    CloseTab("close_tab"),
    ;

    val isTerminal: Boolean
        get() = this == OpenReader || this == CloseTab
}

internal enum class CustomWorkflowIcon(val wireValue: String) {
    Download("download"),
    Reader("reader"),
    Favorite("favorite"),
    Steps("steps"),
}

internal data class CustomWorkflow(
    val id: String,
    val name: String,
    val icon: CustomWorkflowIcon = CustomWorkflowIcon.Steps,
    val steps: List<CustomWorkflowStep>,
    val showInMenu: Boolean = true,
)

internal enum class CustomWorkflowPlacement(val wireValue: String, val slotCount: Int) {
    MenuShortcut("menu_shortcut", 5),
    MenuFooter("menu_footer", 5),
    AddressBar("address_bar", 3),
    LinkLongPress("link_long_press", 1),
    LinkPeekSwipe("link_peek_swipe", 1),
    LinkPeekToolbar("link_peek_toolbar", 3),
}

internal data class CustomWorkflowBinding(
    val placement: CustomWorkflowPlacement,
    val slot: Int,
    val workflowId: String,
)

internal data class CustomWorkflowConfiguration(
    val workflows: List<CustomWorkflow> = emptyList(),
    val bindings: List<CustomWorkflowBinding> = emptyList(),
)

internal enum class CustomWorkflowValidationFailure {
    InvalidId,
    EmptyName,
    NameTooLong,
    InvalidName,
    EmptySteps,
    TooManySteps,
    DuplicateStep,
    TerminalStepNotLast,
    ConflictingSteps,
}

internal object CustomWorkflowRules {
    const val MAX_WORKFLOWS = 64
    const val MAX_STEPS = 5
    const val MAX_NAME_LENGTH = 80
    const val MAX_ID_LENGTH = 64
    val MAX_BINDINGS: Int = CustomWorkflowPlacement.entries.sumOf { it.slotCount }

    private val validId = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")

    fun isValidId(id: String): Boolean = id.length <= MAX_ID_LENGTH && validId.matches(id)

    fun validationFailure(workflow: CustomWorkflow): CustomWorkflowValidationFailure? = when {
        !isValidId(workflow.id) -> CustomWorkflowValidationFailure.InvalidId
        workflow.name.isBlank() -> CustomWorkflowValidationFailure.EmptyName
        workflow.name.length > MAX_NAME_LENGTH -> CustomWorkflowValidationFailure.NameTooLong
        workflow.name.any(Char::isISOControl) -> CustomWorkflowValidationFailure.InvalidName
        workflow.steps.isEmpty() -> CustomWorkflowValidationFailure.EmptySteps
        workflow.steps.size > MAX_STEPS -> CustomWorkflowValidationFailure.TooManySteps
        workflow.steps.distinct().size != workflow.steps.size -> CustomWorkflowValidationFailure.DuplicateStep
        workflow.steps.dropLast(1).any(CustomWorkflowStep::isTerminal) ->
            CustomWorkflowValidationFailure.TerminalStepNotLast
        CustomWorkflowStep.PinTab in workflow.steps && CustomWorkflowStep.CloseTab in workflow.steps ->
            CustomWorkflowValidationFailure.ConflictingSteps
        else -> null
    }

    fun normalize(configuration: CustomWorkflowConfiguration): CustomWorkflowConfiguration {
        val acceptedIds = mutableSetOf<String>()
        val workflows = configuration.workflows.asSequence()
            .take(MAX_WORKFLOWS * 2)
            .map { workflow -> workflow.copy(name = workflow.name.trim(), steps = workflow.steps.toList()) }
            .filter { workflow -> validationFailure(workflow) == null && acceptedIds.add(workflow.id) }
            .take(MAX_WORKFLOWS)
            .toList()
        val acceptedSlots = mutableSetOf<Pair<CustomWorkflowPlacement, Int>>()
        val bindings = configuration.bindings.asSequence()
            .take(MAX_BINDINGS * 2)
            .filter { binding ->
                binding.slot in 0 until binding.placement.slotCount &&
                    isValidId(binding.workflowId) &&
                    acceptedSlots.add(binding.placement to binding.slot)
            }
            .take(MAX_BINDINGS)
            .toList()
        return CustomWorkflowConfiguration(workflows, bindings)
    }

    fun binding(
        configuration: CustomWorkflowConfiguration,
        placement: CustomWorkflowPlacement,
        slot: Int = 0,
    ): CustomWorkflowBinding? = configuration.bindings.firstOrNull { binding ->
        binding.placement == placement && binding.slot == slot && slot in 0 until placement.slotCount
    }

    fun resolve(
        configuration: CustomWorkflowConfiguration,
        placement: CustomWorkflowPlacement,
        slot: Int = 0,
    ): CustomWorkflow? {
        val id = binding(configuration, placement, slot)?.workflowId ?: return null
        return configuration.workflows.firstOrNull { it.id == id && validationFailure(it) == null }
    }

    /** Retain references to deleted definitions so slots stay empty and gestures remain no-ops. */
    fun delete(configuration: CustomWorkflowConfiguration, id: String): CustomWorkflowConfiguration =
        configuration.copy(workflows = configuration.workflows.filterNot { it.id == id })

    fun bind(
        configuration: CustomWorkflowConfiguration,
        placement: CustomWorkflowPlacement,
        slot: Int = 0,
        workflowId: String?,
    ): CustomWorkflowConfiguration {
        if (slot !in 0 until placement.slotCount) return configuration
        if (workflowId != null && !isValidId(workflowId)) return configuration
        val others = configuration.bindings.filterNot { it.placement == placement && it.slot == slot }
        return configuration.copy(
            bindings = if (workflowId == null) others else others + CustomWorkflowBinding(placement, slot, workflowId),
        )
    }
}
