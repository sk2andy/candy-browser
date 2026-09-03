package dev.sk2andy.materialbrowser.data

import android.content.Context
import dev.sk2andy.materialbrowser.browser.ReleaseNotesDocument
import dev.sk2andy.materialbrowser.browser.ReleaseNotesMarkdownRules

internal data class ReleaseNotesContent(
    val versionName: String,
    val document: ReleaseNotesDocument,
)

internal class ReleaseNotesRepository(context: Context) {
    private val assets = context.applicationContext.assets

    fun load(versionName: String): ReleaseNotesContent? = runCatching {
        val bytes = assets.open(RELEASE_NOTES_ASSET).use { input ->
            input.readNBytes(ReleaseNotesMarkdownRules.MAX_MARKDOWN_BYTES + 1)
        }
        if (bytes.size > ReleaseNotesMarkdownRules.MAX_MARKDOWN_BYTES) return null
        val markdown = bytes.toString(Charsets.UTF_8)
        val document = ReleaseNotesMarkdownRules.parse(markdown, versionName) ?: return null
        ReleaseNotesContent(versionName = versionName, document = document)
    }.getOrNull()

    companion object {
        const val RELEASE_NOTES_ASSET = "candy_release_notes.md"
    }
}
