package dev.sk2andy.materialbrowser.browser.annotation

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.sk2andy.materialbrowser.browser.gecko.AndroidBrowserEngineSessionPort
import dev.sk2andy.materialbrowser.data.CanonicalWebUrl
import dev.sk2andy.materialbrowser.data.WebAnnotationStore
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors

internal class WebAnnotationCoordinator(context: Context) : AutoCloseable {
    private val store = WebAnnotationStore(context)
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "web-annotations-io") }
    private val main = Handler(Looper.getMainLooper())
    private var binding by mutableStateOf<Binding?>(null)
    private var suspended by mutableStateOf(false)
    private var closed = false
    private var interactionRevision = 0L
    val canOpenPageAnnotations: Boolean
        get() = binding?.let(::current) == true
    var editor by mutableStateOf<WebAnnotation?>(null)
        private set
    var reader by mutableStateOf<WebAnnotation?>(null)
        private set
    var scrollFailed by mutableStateOf(false)
        private set
    var pageAnnotations by mutableStateOf<List<WebAnnotation>>(emptyList())
        private set
    var isLibraryVisible by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    var isSaving by mutableStateOf(false)
        private set

    private data class Binding(
        val session: AndroidBrowserEngineSessionPort,
        val profileId: String,
        val url: String,
        val generation: Int,
        val isCurrent: () -> Boolean,
    )

    fun bind(
        session: AndroidBrowserEngineSessionPort,
        profileId: String,
        url: String,
        generation: Int,
        isActive: Boolean = true,
        isCurrent: () -> Boolean,
    ) {
        val canonical = CanonicalWebUrl.key(url) ?: return detach()
        if (closed) return
        val old = binding
        val sameBinding = old?.session === session && old.profileId == profileId && old.url == canonical && old.generation == generation
        if (!isActive) {
            if (sameBinding) suspend() else detach()
            return
        }
        if (sameBinding && old != null) {
            if (suspended) {
                suspended = false
                val resumed = old.copy(isCurrent = isCurrent)
                binding = resumed
                listen(resumed)
                load(resumed)
            }
            return
        }
        detach()
        val next = Binding(session, profileId, canonical, generation, isCurrent)
        binding = next
        listen(next)
        load(next)
    }

    private fun listen(expected: Binding) {
        expected.session.setWebAnnotationClickListener { id ->
            if (current(expected)) pageAnnotations.firstOrNull { it.id == id }?.let(::read)
        }
    }

    private fun load(expected: Binding) {
        worker.execute {
            val values = store.all().filter { it.profileId == expected.profileId && it.url == expected.url }
            main.post {
                if (!current(expected)) return@post
                pageAnnotations = values
                val quotes = JSONArray().apply {
                    values.forEach { put(JSONObject().put("id", it.id).put("quote", it.quote)) }
                }
                val payload = JSONObject()
                    .put("operation", "highlights")
                    .put("url", expected.url)
                    .put("annotations", quotes)
                expected.session.executeWebTextOperation(payload.toString()) { }
            }
        }
    }

    fun select(text: String, title: String) {
        val expected = binding ?: return
        if (isSaving || !current(expected) || text.isBlank() || text.length > WebAnnotationRules.MAX_QUOTE_CHARS) return
        interactionRevision++
        editor = pageAnnotations.firstOrNull { it.quote == text } ?: WebAnnotation(
            id = UUID.randomUUID().toString(),
            profileId = expected.profileId,
            url = expected.url,
            title = title.take(512),
            quote = text,
            note = "",
            createdAt = System.currentTimeMillis(),
        )
        failed = false
        reader = null
        scrollFailed = false
    }

    fun save(note: String) {
        val expected = binding ?: return
        val value = editor ?: return
        if (isSaving || !current(expected) || note.length > WebAnnotationRules.MAX_NOTE_CHARS) return
        val revision = ++interactionRevision
        isSaving = true
        failed = false
        val cleanupEpoch = WebAnnotationStore.epoch
        worker.execute {
            val saved = store.save(
                annotation = value.copy(note = note),
                isPrivate = false,
                expectedEpoch = cleanupEpoch,
            )
            main.post {
                if (!current(expected)) return@post
                if (revision == interactionRevision) {
                    isSaving = false
                    failed = !saved
                    if (saved) {
                        editor = null
                        reader = value.copy(note = note.trim())
                    }
                }
                if (saved) load(expected)
            }
        }
    }

    fun delete(value: WebAnnotation) {
        val expected = binding ?: return
        if (isSaving || !current(expected) || value !in pageAnnotations) return
        val revision = ++interactionRevision
        isSaving = true
        failed = false
        worker.execute {
            val deleted = store.delete(value.id, expected.profileId)
            main.post {
                if (!current(expected)) return@post
                if (revision == interactionRevision) {
                    isSaving = false
                    failed = !deleted
                    if (deleted) {
                        editor = null
                        reader = null
                    }
                }
                if (deleted) load(expected)
            }
        }
    }

    fun showPageAnnotations() {
        if (isSaving || !canOpenPageAnnotations) return
        interactionRevision++
        reader = null
        editor = null
        scrollFailed = false
        isLibraryVisible = true
    }

    fun read(value: WebAnnotation) {
        val expected = binding ?: return
        if (isSaving || !current(expected) || value !in pageAnnotations) return
        interactionRevision++
        reader = value
        editor = null
        scrollFailed = false
    }

    fun scrollTo(value: WebAnnotation) {
        val expected = binding ?: return
        if (isSaving || !current(expected) || value !in pageAnnotations) return
        val revision = ++interactionRevision
        val payload = JSONObject()
            .put("operation", "scroll-to-annotation")
            .put("url", expected.url)
            .put("id", value.id)
            .put("quote", value.quote)
        expected.session.executeWebTextOperation(payload.toString()) { scrolled ->
            if (!current(expected) || revision != interactionRevision) return@executeWebTextOperation
            isLibraryVisible = false
            editor = null
            reader = if (scrolled) null else value
            scrollFailed = !scrolled
        }
    }

    fun edit(value: WebAnnotation) {
        val expected = binding ?: return
        if (isSaving || !current(expected) || value !in pageAnnotations) return
        interactionRevision++
        editor = value
        reader = null
        failed = false
    }

    fun dismissEditor() {
        interactionRevision++
        isSaving = false
        editor = null
        failed = false
    }

    fun dismissLibrary() {
        interactionRevision++
        isLibraryVisible = false
    }

    fun dismissReader() {
        interactionRevision++
        reader = null
        scrollFailed = false
    }

    fun deleteProfile(profileId: String) {
        if (binding?.profileId == profileId) detach()
        worker.execute { store.deleteProfile(profileId) }
    }

    fun suspend() {
        if (editor == null) return detach()
        interactionRevision++
        suspended = true
        binding?.session?.setWebAnnotationClickListener(null)
        isSaving = false
        reader = null
        scrollFailed = false
        isLibraryVisible = false
        failed = false
    }

    fun detach() {
        interactionRevision++
        binding?.session?.setWebAnnotationClickListener(null)
        binding = null
        suspended = false
        isSaving = false
        editor = null
        reader = null
        scrollFailed = false
        pageAnnotations = emptyList()
        isLibraryVisible = false
        failed = false
    }

    private fun current(expected: Binding): Boolean = !closed && !suspended && binding === expected && expected.isCurrent()

    override fun close() {
        closed = true
        detach()
        worker.shutdown()
    }
}
