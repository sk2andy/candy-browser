# Saved web highlights and inline text operations

## Product contract

| Entry point | Behavior |
| --- | --- |
| Android native text selection → Highlight + note | Saves the original selected quote and an optional note in the current regular profile. Native copy, Select all, share and installed PROCESS_TEXT apps remain engine-owned. |
| Android native text selection → Translate inline | Starts the inline translation flow; the translation coordinator selects the backend and validates the pending document identity. |
| Page menu → Highlights | Lists the current page’s saved quotes and notes, including quotes that no longer match the page. Tap a quote to edit its note or delete it. |
| Revisit a page | Reapplies uniquely matching, visible, non-editable quotes using the CSS Custom Highlight API. No page DOM rewriting is needed for highlights. |
| Recall query | When Recall is enabled, saved quotes and notes join profile-scoped local Recall results. Saving a quote does not enable Recall or overwrite its extracted page text. |

## Ownership and boundaries

| Layer | Owner / invariant |
| --- | --- |
| Model | `browser/annotation/WebAnnotationRules`: bounded input, HTTP(S) page key, profile filtering and local note/quote search. |
| Storage | `data/WebAnnotationStore`: serialized atomic JSON in `noBackupFilesDir/web_annotations.json`; private saves rejected at the store boundary. |
| Coordination | `WebAnnotationCoordinator`: immutable session/profile/page/navigation binding; stale asynchronous results rejected; explicit save, edit and delete. |
| Android selection | `GeckoWebTextSelectionDelegate` extends Gecko’s built-in delegate. `systemwebview/WebTextActionModeCallback` decorates the original callback, including content-rectangle and lifecycle delegation. |
| Page operation | `web_text_operations.js`: exact unique text match, hidden and editable subtree exclusion, bounded traversal; translation toggles only after trusted click/keyboard events. |
| Engine boundary | Gecko messages validate session token, policy revision and navigation generation. System WebView validates native generation and URL, then a document token before mutation. |
| Other platforms | Android implementation only. The iOS menu capability is disabled until a native selection/annotation adapter exists. |

## Retention, privacy and limits

| Rule | Result |
| --- | --- |
| Private and preview sessions | No persistent highlights. Candy actions are absent in preview sessions; private sessions retain translation but omit Highlight. |
| Explicit save | At most 200 annotations total; a full library rejects a new save instead of silently deleting saved work. Quotes: 8,000 characters; notes: 4,000; page URLs: 4,096. |
| Page identity | HTTP(S) canonical URL, including query and excluding fragment, within one profile. |
| Changed or repeated quote | Never guess an anchor. Original quote stays in Highlights and Recall, even if visual reattachment is impossible. |
| Dynamic content | Reattachment occurs on committed page visits and saved annotation changes. Content inserted later may require revisiting the page. |
| Recall/history clearing | Explicit saved annotations remain, like bookmarks. Delete individual entries in Highlights or delete their profile to remove them. Recall’s index clear never silently deletes user-authored notes. |
| Profile deletion | Removes that profile’s saved annotations. |
| Transfer | Annotation storage and atomic-file sidecars excluded from manual ZIP and Android backup; import/recovery clears old local annotation storage and invalidates already queued saves with a cleanup epoch. No cloud sync or background fetch. |
| Translation | The injected replacement is temporary and toggles between translation and original nodes; forms, editable controls and ambiguous matches are never replaced. Backend/provider handling lives in `page-translation.md`. |

## Verification lookup

| Contract | Test |
| --- | --- |
| Normalization, original quote retention, bounds and profile-scoped search | `WebAnnotationRulesTest` |
| Atomic storage roundtrip, private rejection, corruption, profile cleanup, Recall integration and archive exclusion | `WebAnnotationStoreInstrumentedTest` |
| Native selection action coexistence, original content rectangle, real WebView highlight and trusted translation toggle | `WebTextOperationsInstrumentedTest` |
| Real Gecko extension highlight/translation bridge and protected editable content | `GeckoReaderViewInstrumentedTest.savedHighlightAndInlineTranslationUseDocumentBoundGeckoBridge` |
