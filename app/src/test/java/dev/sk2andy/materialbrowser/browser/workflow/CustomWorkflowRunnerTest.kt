package dev.sk2andy.materialbrowser.browser.workflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomWorkflowRunnerTest {
    @Test
    fun `failed save never closes the source tab`() {
        val run = RecordingRun()
        assertTrue(run.start())

        run.complete(0, CustomWorkflowFailure.SaveFailed)

        assertEquals(listOf(CustomWorkflowStep.SaveReaderOffline), run.executed)
        assertEquals(listOf(CustomWorkflowStepStatus.Failed, CustomWorkflowStepStatus.Skipped), run.state.statuses)
        assertEquals(CustomWorkflowFailure.SaveFailed, run.state.failure)
        assertFalse(run.state.running)
        assertEquals(1, run.finishedCount)
    }

    @Test
    fun `source change after async save preserves success but skips closing`() {
        val run = RecordingRun()
        run.start()
        run.current = false

        run.complete(0)

        assertEquals(listOf(CustomWorkflowStep.SaveReaderOffline), run.executed)
        assertEquals(listOf(CustomWorkflowStepStatus.Succeeded, CustomWorkflowStepStatus.Skipped), run.state.statuses)
        assertEquals(CustomWorkflowFailure.PageChanged, run.state.failure)
        assertEquals(1, run.finishedCount)
    }

    @Test
    fun `duplicate async completion cannot advance or finish a different step`() {
        val run = RecordingRun()
        run.start()
        run.complete(0)

        run.complete(0, CustomWorkflowFailure.SaveFailed)
        run.complete(0)

        assertEquals(listOf(CustomWorkflowStep.SaveReaderOffline, CustomWorkflowStep.CloseTab), run.executed)
        assertEquals(listOf(CustomWorkflowStepStatus.Succeeded, CustomWorkflowStepStatus.Running), run.state.statuses)
        assertTrue(run.state.running)
        assertEquals(0, run.finishedCount)
        run.complete(1)
        run.complete(1)
        assertEquals(1, run.finishedCount)
        assertNull(run.state.failure)
    }

    @Test
    fun `cancellation waits for pending completion and preserves its successful result`() {
        val run = RecordingRun()
        run.start()

        run.runner.cancel()

        assertTrue(run.state.running)
        assertTrue(run.state.cancellationRequested)
        assertEquals(0, run.finishedCount)
        assertEquals(listOf(CustomWorkflowStepStatus.Running, CustomWorkflowStepStatus.Waiting), run.state.statuses)

        run.complete(0)

        assertEquals(listOf(CustomWorkflowStep.SaveReaderOffline), run.executed)
        assertEquals(listOf(CustomWorkflowStepStatus.Succeeded, CustomWorkflowStepStatus.Skipped), run.state.statuses)
        assertEquals(CustomWorkflowFailure.Cancelled, run.state.failure)
        assertFalse(run.state.running)
        assertEquals(1, run.finishedCount)
    }

    @Test
    fun `failed pending write after cancellation reports its actual failure`() {
        val run = RecordingRun()
        run.start()
        run.runner.cancel()

        run.complete(0, CustomWorkflowFailure.SaveFailed)

        assertEquals(listOf(CustomWorkflowStep.SaveReaderOffline), run.executed)
        assertEquals(CustomWorkflowFailure.SaveFailed, run.state.failure)
        assertEquals(listOf(CustomWorkflowStepStatus.Failed, CustomWorkflowStepStatus.Skipped), run.state.statuses)
    }

    @Test
    fun `second start while running cannot replace input actions or cleanup`() {
        val run = RecordingRun()
        run.start()
        var replacedCleanup = false
        var replacedExecution = false

        val started = run.runner.start(
            workflow = workflow(CustomWorkflowStep.CopyUrl),
            input = input().copy(tabId = "other-tab"),
            isCurrent = { false },
            execute = { _, _ -> replacedExecution = true },
            onFinished = { replacedCleanup = true },
        )
        run.complete(0)
        run.complete(1)

        assertFalse(started)
        assertFalse(replacedExecution)
        assertFalse(replacedCleanup)
        assertEquals(listOf(CustomWorkflowStep.SaveReaderOffline, CustomWorkflowStep.CloseTab), run.executed)
        assertNull(run.state.failure)
        assertEquals(1, run.finishedCount)
    }

    @Test
    fun `private persistence rejects entire sequence before an earlier copy effect`() {
        listOf(CustomWorkflowStep.SaveReaderOffline, CustomWorkflowStep.AddFavorite).forEach { persistentStep ->
            val run = RecordingRun()

            run.start(
                workflow = workflow(CustomWorkflowStep.CopyUrl, persistentStep),
                input = input().copy(isPrivate = true),
            )

            assertTrue(run.executed.isEmpty())
            assertEquals(CustomWorkflowFailure.PrivatePersistence, run.state.failure)
            assertEquals(listOf(CustomWorkflowStepStatus.Skipped, CustomWorkflowStepStatus.Skipped), run.state.statuses)
            assertEquals(1, run.finishedCount)
        }
    }

    @Test
    fun `pinned source close is rejected before saving`() {
        val run = RecordingRun()

        run.start(input = input().copy(isPinned = true))

        assertTrue(run.executed.isEmpty())
        assertEquals(CustomWorkflowFailure.PinnedTab, run.state.failure)
        assertEquals(listOf(CustomWorkflowStepStatus.Skipped, CustomWorkflowStepStatus.Skipped), run.state.statuses)
    }

    @Test
    fun `pinned containing tab does not reject a link workflow`() {
        val run = RecordingRun()

        run.start(input = input().copy(isPinned = true, isLink = true))

        assertEquals(listOf(CustomWorkflowStep.SaveReaderOffline), run.executed)
        assertNull(run.state.failure)
        run.complete(0)
        run.complete(1)
        assertNull(run.state.failure)
    }

    @Test
    fun `terminal close succeeds when closing invalidates the source`() {
        val run = RecordingRun()
        run.start()
        run.complete(0)
        run.current = false

        run.complete(1)

        assertFalse(run.state.running)
        assertNull(run.state.failure)
        assertEquals(listOf(CustomWorkflowStepStatus.Succeeded, CustomWorkflowStepStatus.Succeeded), run.state.statuses)
        assertEquals(1, run.finishedCount)
    }

    @Test
    fun `timeout discards late completion and prevents following effects`() {
        val run = RecordingRun()
        run.start()

        run.runner.timeout()
        val timedOutState = run.state
        run.complete(0)
        run.runner.timeout()

        assertEquals(CustomWorkflowFailure.TimedOut, run.state.failure)
        assertEquals(timedOutState, run.state)
        assertEquals(listOf(CustomWorkflowStep.SaveReaderOffline), run.executed)
        assertEquals(1, run.finishedCount)
    }

    @Test
    fun `disposed run cannot resume from late completion or alter a subsequent run`() {
        val run = RecordingRun()
        run.start()
        run.runner.dispose()
        assertEquals(CustomWorkflowFailure.Cancelled, run.state.failure)
        assertEquals(1, run.finishedCount)

        run.start(workflow(CustomWorkflowStep.CopyUrl))
        val nextRunState = run.state
        run.complete(0)

        assertEquals(nextRunState, run.state)
        assertEquals(listOf(CustomWorkflowStep.SaveReaderOffline, CustomWorkflowStep.CopyUrl), run.executed)
        assertEquals(1, run.finishedCount)
        run.complete(1)
        assertEquals(2, run.finishedCount)
        assertNull(run.state.failure)
    }

    @Test
    fun `invalid sequence and stale source cannot execute any effect`() {
        val invalid = RecordingRun()
        invalid.start(workflow(CustomWorkflowStep.CloseTab, CustomWorkflowStep.CopyUrl))
        assertTrue(invalid.executed.isEmpty())
        assertEquals(CustomWorkflowFailure.InvalidWorkflow, invalid.state.failure)

        val stale = RecordingRun().apply { current = false }
        stale.start()
        assertTrue(stale.executed.isEmpty())
        assertEquals(CustomWorkflowFailure.PageChanged, stale.state.failure)
    }

    private class RecordingRun {
        var current = true
        var finishedCount = 0
        val executed = mutableListOf<CustomWorkflowStep>()
        private val completions = mutableListOf<(CustomWorkflowFailure?) -> Unit>()
        val runner = CustomWorkflowRunner {}
        val state: CustomWorkflowRunState get() = requireNotNull(runner.state)

        fun start(
            workflow: CustomWorkflow = workflow(CustomWorkflowStep.SaveReaderOffline, CustomWorkflowStep.CloseTab),
            input: CustomWorkflowInput = input(),
        ): Boolean = runner.start(
            workflow = workflow,
            input = input,
            isCurrent = { current },
            execute = { step, completed ->
                executed += step
                completions += completed
            },
            onFinished = { finishedCount++ },
        )

        fun complete(index: Int, failure: CustomWorkflowFailure? = null) = completions[index](failure)
    }

    private companion object {
        fun workflow(vararg steps: CustomWorkflowStep) = CustomWorkflow(
            id = "save-later",
            name = "Save for later",
            icon = CustomWorkflowIcon.Download,
            steps = steps.toList(),
        )

        fun input() = CustomWorkflowInput(
            tabId = "source-tab",
            profileId = "work",
            url = "https://example.test/article",
            title = "Article",
            isPrivate = false,
            isPinned = false,
            isLink = false,
        )
    }
}
