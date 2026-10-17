package dev.sk2andy.materialbrowser.browser.formdraft

/** Main-thread, tab-scoped drafts. No persistence dependency by design. */
internal class PrivateFormDrafts {
    private data class Entry(val tabId: String, val draft: FormDraft)

    private var entries = emptyList<Entry>()

    fun load(tabId: String, profileId: String, url: String, now: Long): FormDraft? {
        prune(now)
        return entries.firstOrNull {
            it.tabId == tabId && it.draft.profileId == profileId && it.draft.url == url
        }?.draft
    }

    fun update(tabId: String, draft: FormDraft, delete: Boolean, now: Long) {
        entries = entries.filterNot {
            it.tabId == tabId && it.draft.profileId == draft.profileId && it.draft.url == draft.url
        }
        if (!delete) FormDraftRules.sanitize(draft, now)?.let {
            entries = entries + Entry(tabId, it)
        }
        prune(now)
    }

    fun deleteTab(tabId: String) {
        entries = entries.filterNot { it.tabId == tabId }
    }

    fun deleteProfile(profileId: String) {
        entries = entries.filterNot { it.draft.profileId == profileId }
    }

    fun clear() {
        entries = emptyList()
    }

    private fun prune(now: Long) {
        entries = entries.filter { FormDraftRules.sanitize(it.draft, now) != null }
            .sortedWith(compareByDescending<Entry> { it.draft.updatedAt }
                .thenBy { it.tabId }.thenBy { it.draft.url })
            .take(FormDraftRules.MAX_PAGES)
    }
}
