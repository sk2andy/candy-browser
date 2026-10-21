package dev.sk2andy.materialbrowser.browser.workflow

internal enum class CustomWorkflowFailure {
    InvalidWorkflow,
    UnsupportedPage,
    PrivatePersistence,
    PinnedTab,
    PageChanged,
    EmptyArticle,
    SaveFailed,
    LibraryFull,
    ActionFailed,
    Cancelled,
    TimedOut,
}

internal enum class CustomWorkflowStepStatus { Waiting, Running, Succeeded, Failed, Skipped }

internal data class CustomWorkflowInput(
    val tabId: String,
    val profileId: String,
    val url: String,
    val title: String,
    val isPrivate: Boolean,
    val isPinned: Boolean,
    val isLink: Boolean,
)

internal data class CustomWorkflowRunState(
    val workflow: CustomWorkflow,
    val statuses: List<CustomWorkflowStepStatus>,
    val running: Boolean,
    val failure: CustomWorkflowFailure? = null,
    val cancellationRequested: Boolean = false,
)

internal object CustomWorkflowExecutionRules {
    fun rejection(workflow: CustomWorkflow, input: CustomWorkflowInput): CustomWorkflowFailure? = when {
        CustomWorkflowRules.validationFailure(workflow) != null -> CustomWorkflowFailure.InvalidWorkflow
        input.isPrivate && workflow.steps.any {
            it == CustomWorkflowStep.SaveReaderOffline || it == CustomWorkflowStep.AddFavorite
        } -> CustomWorkflowFailure.PrivatePersistence
        !input.isLink && input.isPinned && CustomWorkflowStep.CloseTab in workflow.steps ->
            CustomWorkflowFailure.PinnedTab
        else -> null
    }
}

/** One run, one captured input. Async completions cannot advance a different step or run. */
internal class CustomWorkflowRunner(
    private val onChanged: (CustomWorkflowRunState) -> Unit,
) {
    var state: CustomWorkflowRunState? = null
        private set
    private var generation = 0L
    private var isCurrent: () -> Boolean = { false }
    private var execute: (CustomWorkflowStep, (CustomWorkflowFailure?) -> Unit) -> Unit = { _, _ -> }
    private var onFinished: () -> Unit = {}

    fun start(
        workflow: CustomWorkflow,
        input: CustomWorkflowInput,
        isCurrent: () -> Boolean,
        execute: (CustomWorkflowStep, (CustomWorkflowFailure?) -> Unit) -> Unit,
        onFinished: () -> Unit,
    ): Boolean {
        if (state?.running == true) return false
        generation++
        this.isCurrent = isCurrent
        this.execute = execute
        this.onFinished = onFinished
        publish(CustomWorkflowRunState(workflow, workflow.steps.map { CustomWorkflowStepStatus.Waiting }, true))
        val rejection = CustomWorkflowExecutionRules.rejection(workflow, input)
        if (rejection != null) finish(rejection) else advance(0)
        return true
    }

    fun cancel() {
        val current = state?.takeIf { it.running } ?: return
        // A submitted disk write may still finish. Report its real outcome, then stop the chain.
        publish(current.copy(cancellationRequested = true))
    }

    fun timeout() {
        if (state?.running == true) finish(CustomWorkflowFailure.TimedOut)
    }

    fun dispose() {
        if (state?.running == true) finish(CustomWorkflowFailure.Cancelled)
        generation++
    }

    private fun advance(index: Int) {
        val current = state ?: return
        if (current.cancellationRequested) {
            finish(CustomWorkflowFailure.Cancelled)
            return
        }
        if (index == current.workflow.steps.size) {
            finish(null)
            return
        }
        if (!isCurrent()) {
            finish(CustomWorkflowFailure.PageChanged)
            return
        }
        val runGeneration = generation
        publish(current.copy(statuses = current.statuses.toMutableList().apply {
            this[index] = CustomWorkflowStepStatus.Running
        }))
        execute(current.workflow.steps[index]) { failure ->
            val latest = state ?: return@execute
            if (generation != runGeneration || !latest.running ||
                latest.statuses.getOrNull(index) != CustomWorkflowStepStatus.Running
            ) return@execute
            publish(latest.copy(statuses = latest.statuses.toMutableList().apply {
                this[index] = if (failure == null) CustomWorkflowStepStatus.Succeeded else CustomWorkflowStepStatus.Failed
            }))
            if (failure != null) finish(failure) else advance(index + 1)
        }
    }

    private fun finish(failure: CustomWorkflowFailure?) {
        val current = state ?: return
        publish(current.copy(
            running = false,
            failure = failure,
            statuses = current.statuses.map { status ->
                when (status) {
                    CustomWorkflowStepStatus.Waiting -> CustomWorkflowStepStatus.Skipped
                    CustomWorkflowStepStatus.Running -> CustomWorkflowStepStatus.Failed
                    else -> status
                }
            },
        ))
        val cleanup = onFinished
        onFinished = {}
        isCurrent = { false }
        execute = { _, _ -> }
        cleanup()
    }

    private fun publish(value: CustomWorkflowRunState) {
        state = value
        onChanged(value)
    }
}
