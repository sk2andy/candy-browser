package dev.sk2andy.materialbrowser.browser.workflow

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy
import dev.sk2andy.materialbrowser.data.CustomWorkflowRepository
import dev.sk2andy.materialbrowser.reader.ReaderDocument
import dev.sk2andy.materialbrowser.reader.ReaderExtractionFailure
import dev.sk2andy.materialbrowser.reader.ReaderExtractionResult
import dev.sk2andy.materialbrowser.reader.ReaderLibraryRepository

internal data class CustomWorkflowReaderRequest(
    val input: CustomWorkflowInput,
    val document: ReaderDocument,
)

/** Android effects for the pure sequential runner; page data exists only for the current run. */
internal class BrowserCustomWorkflowRuntime(
    context: Context,
    private val controller: BrowserController,
) {
    private val repository = CustomWorkflowRepository.get(context)
    private val readerLibrary = ReaderLibraryRepository.get(context)
    private val handler = Handler(Looper.getMainLooper())
    var configuration by mutableStateOf(CustomWorkflowConfiguration())
        private set
    var runState by mutableStateOf<CustomWorkflowRunState?>(null)
        private set
    var readerRequest by mutableStateOf<CustomWorkflowReaderRequest?>(null)
        private set
    private var closed = false
    private var loaded = false
    private var saving = false
    private var runToken = 0L
    private var releaseExtraction: (() -> Unit)? = null
    private var timeout: Runnable? = null
    private val runner: CustomWorkflowRunner = CustomWorkflowRunner { state ->
        runState = state
        timeout?.let(handler::removeCallbacks)
        timeout = if (state.running) Runnable { runnerTimeout() }.also {
            handler.postDelayed(it, STEP_TIMEOUT_MILLIS)
        } else null
    }

    fun load() {
        repository.load { value ->
            if (!closed && !loaded) {
                configuration = value
                loaded = true
            }
        }
    }

    fun update(configuration: CustomWorkflowConfiguration, onResult: (Boolean) -> Unit) {
        if (!loaded || closed || saving) {
            onResult(false)
            return
        }
        saving = true
        repository.save(configuration) { success ->
            saving = false
            if (!closed && success) this.configuration = configuration
            onResult(success && !closed)
        }
    }

    fun run(id: String, inputUrl: String? = null, previewView: View? = null) {
        if (closed || !loaded || runState?.running == true) return
        // Missing definitions deliberately do nothing; a deleted assignment is not a fallback.
        val workflow = configuration.workflows.firstOrNull { it.id == id } ?: return
        val token = ++runToken
        val tab = controller.selectedTab
        val input = CustomWorkflowInput(
            tabId = tab.id,
            profileId = tab.profileId,
            url = BrowserUriPolicy.normalizeHttpUrl(inputUrl ?: tab.url).orEmpty(),
            title = if (inputUrl == null) tab.title else inputUrl,
            isPrivate = tab.isIncognito,
            isPinned = tab.isPinned,
            isLink = inputUrl != null,
        )
        val identity = controller.selectedActivityResultIdentity()
        val sourceUrl = tab.url
        val previewRevision = controller.contentActions.revision
        var document: ReaderDocument? = null
        fun active(): Boolean = token == runToken && runner.state?.running == true &&
            runner.state?.cancellationRequested == false
        fun current(): Boolean = !closed && active() && controller.canRunCustomWorkflow &&
            identity != null && controller.isActivityResultIdentityCurrent(identity) &&
            controller.selectedTab.profileId == input.profileId &&
            controller.selectedTab.url == sourceUrl &&
            (!input.isLink || previewView == null || controller.contentActions.revision == previewRevision)

        fun extract(onResult: (ReaderDocument?, CustomWorkflowFailure?) -> Unit) {
            document?.let { onResult(it, null); return }
            val accept: (ReaderExtractionResult) -> Unit = { result ->
                if (!current()) {
                    onResult(null, CustomWorkflowFailure.PageChanged)
                } else when (result) {
                    is ReaderExtractionResult.Success -> {
                        document = result.document
                        onResult(result.document, null)
                    }
                    is ReaderExtractionResult.Failure -> onResult(null, when (result.reason) {
                        ReaderExtractionFailure.EmptyArticle -> CustomWorkflowFailure.EmptyArticle
                        ReaderExtractionFailure.UnsupportedPage -> CustomWorkflowFailure.UnsupportedPage
                        ReaderExtractionFailure.InvalidResponse -> CustomWorkflowFailure.ActionFailed
                    })
                }
            }
            if (input.isLink) {
                val release = controller.extractCustomWorkflowLink(input.url, previewView, accept)
                if (active()) releaseExtraction = release else release()
            } else controller.extractSelectedPageForReader(accept)
        }

        runner.start(
            workflow = workflow,
            input = input,
            isCurrent = { input.url.isNotEmpty() && current() },
            execute = { step, completed ->
                when (step) {
                    CustomWorkflowStep.SaveReaderOffline -> extract { article, failure ->
                        if (article == null) completed(failure ?: CustomWorkflowFailure.EmptyArticle)
                        else readerLibrary.saveWorkflowSnapshot(
                            document = article,
                            sourceProfileId = input.profileId,
                            isPrivate = input.isPrivate,
                            isCurrent = { current() && runner.state?.cancellationRequested == false },
                        ) { snapshot ->
                            completed(if (snapshot != null) null else CustomWorkflowFailure.SaveFailed)
                        }
                    }
                    CustomWorkflowStep.AddFavorite -> {
                        if (controller.isFavorite(input.url)) completed(null)
                        else controller.addFavorite(input.url, document?.title ?: input.title) { success ->
                            completed(if (success) null else CustomWorkflowFailure.SaveFailed)
                        }
                    }
                    CustomWorkflowStep.CopyUrl -> {
                        controller.copyLink(input.url)
                        completed(null)
                    }
                    CustomWorkflowStep.PinTab -> {
                        val targetId = if (input.isLink) controller.createBackgroundTab(
                            initialUrl = input.url,
                            openerTabId = input.tabId,
                            isIncognito = input.isPrivate,
                            showContentActionFeedback = false,
                        ) else input.tabId
                        val success = targetId != null && (
                            controller.tabs.any { it.id == targetId && it.isPinned } ||
                                controller.setTabPinned(targetId, true)
                            )
                        completed(if (success) null else CustomWorkflowFailure.ActionFailed)
                    }
                    CustomWorkflowStep.OpenReader -> extract { article, failure ->
                        if (article == null) completed(failure ?: CustomWorkflowFailure.EmptyArticle)
                        else {
                            readerRequest = CustomWorkflowReaderRequest(input, article)
                            completed(null)
                        }
                    }
                    CustomWorkflowStep.CloseTab -> {
                        if (input.isLink) {
                            // A link's containing tab is never the target of a close step.
                            completed(null)
                        } else if (controller.selectedTab.isPinned) completed(CustomWorkflowFailure.PinnedTab)
                        else {
                            controller.closeTabFromUser(input.tabId)
                            completed(if (controller.tabs.none { it.id == input.tabId }) null else CustomWorkflowFailure.ActionFailed)
                        }
                    }
                }
            },
            onFinished = {
                releaseExtraction?.invoke()
                releaseExtraction = null
                document = null
                if (input.isLink && workflow.steps.any { it.isTerminal } &&
                    controller.contentActions.revision == previewRevision &&
                    runner.state?.failure == null
                ) controller.contentActions.dismiss()
            },
        )
    }

    fun cancel() = runner.cancel()

    fun dismissResult() {
        if (runState?.running != true) runState = null
    }

    fun consumeReader(): CustomWorkflowReaderRequest? = readerRequest.also { readerRequest = null }

    fun close() {
        closed = true
        runner.dispose()
        timeout?.let(handler::removeCallbacks)
        timeout = null
        readerRequest = null
        runState = null
    }

    private fun runnerTimeout() {
        runner.timeout()
    }

    private companion object {
        const val STEP_TIMEOUT_MILLIS = 30_000L
    }
}
