# Saved web highlights and inline text operations

## Product contract

| Entry point | Behavior |
| --- | --- |
| Android native text selection → Highlight + note | Saves the original selected quote and an optional note in the current regular profile. Native copy, Select all, share and installed PROCESS_TEXT apps remain engine-owned. |
| Android native text selection → Translate inline | Starts the inline translation flow; the translation coordinator selects the backend and validates the pending document identity. |
| Page menu → Highlights | Shows a Material bottom sheet with compact segmented list items: quote, readable note preview and a trailing menu for Read note / Edit. Tap the row to scroll to its unique visible text match and close the list. Missing or ambiguous text opens the saved note with an explanation. |
| Tap a page highlight | A trusted short tap opens the native note reader. Selection, dragging and synthetic clicks do not open notes. Edit remains a separate action. |
| Note reader | Uses a Material bottom sheet with saved quote, Edit and Close actions. Renders Markdown headings, bold/italic text, lists, quotes, code and HTTP(S) links. Links open a selected new tab in the current profile. HTML and images remain inert text; malformed markup stays readable. |
| Note editor | Full-screen on compact windows, bounded dialog on larger windows. A borderless, internally scrolling text editor fills the available height without a visible title, label or placeholder. Delete sits beside Save in the top bar. A formatting toolbar inserts bold, italic, headings, bullet lists, links and inline code at the selection; a separate Preview tab renders the current draft and stays inert so links cannot navigate away from an unsaved draft. Save is explicit, drafts survive view recreation and temporary background suspension of the same document; Close/Back asks before discarding changes. Pending saves lock typing and formatting until completion. |
| Revisit a page | Reapplies uniquely matching, visible, non-editable quotes using the CSS Custom Highlight API. No page DOM rewriting is needed for highlights. |
| Recall query | When Recall is enabled, saved quotes and notes join profile-scoped local Recall results. Saving a quote does not enable Recall or overwrite its extracted page text. |

## Ownership and boundaries

| Layer | Owner / invariant |
| --- | --- |
| Model | `browser/annotation/WebAnnotationRules`: bounded input, HTTP(S) page key, profile filtering and local note/quote search. |
| Storage | `data/WebAnnotationStore`: serialized atomic JSON in `noBackupFilesDir/web_annotations.json`; private saves rejected at the store boundary. |
| Coordination | `WebAnnotationCoordinator`: immutable session/profile/page/navigation binding; page clicks resolve only known annotation IDs; stale asynchronous results and superseded scroll callbacks rejected; explicit read, save, edit and delete. |
| Presentation | `WebAnnotationDialogs`, `WebAnnotationNoteEditor`, `WebAnnotationMarkdown`, `WebAnnotationMarkdownRules`: themed Material list items, bounded Markdown parsing and readable previews; link destinations validated with `BrowserUriPolicy` before tab creation. |
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
| Selection-aware formatting, multiline boundaries and character limits | `WebAnnotationEditorRulesTest` |
| Editor formatting, live preview, draft restoration, discard and pending save | `WebAnnotationNoteEditorInstrumentedTest` |
| Markdown structure, formatting, bounded previews and unsafe-link rejection | `WebAnnotationMarkdownRulesTest`, `WebAnnotationMarkdownInstrumentedTest` |
| List scroll/read/edit actions, readable missing quotes and stale callback rejection | `WebAnnotationDialogsInstrumentedTest`, `WebAnnotationCoordinatorInstrumentedTest` |
| Atomic storage roundtrip, private rejection, corruption, profile cleanup, Recall integration and archive exclusion | `WebAnnotationStoreInstrumentedTest` |
| Native selection action coexistence, original content rectangle, real WebView highlight and trusted translation toggle | `WebTextOperationsInstrumentedTest` |
| Real Gecko extension highlight/translation bridge and protected editable content | `GeckoReaderViewInstrumentedTest.savedHighlightAndInlineTranslationUseDocumentBoundGeckoBridge` |

## Material presentation

| Source | Application |
| --- | --- |
| `.agents/skills/m3-expressive/references/components/containment.md` | Multi-action list: primary quote/scroll area, supporting note preview, trailing action menu. |
| `.agents/skills/m3-expressive/references/component-tokens.md`, List | Segmented gap 2dp, outer CornerLarge and inner CornerExtraSmall; native Material `ListItem` provides typography, spacing and touch targets. |
| App design decision | Bottom sheet replaces the narrow alert dialog; quote uses up to two lines, note preview one line. No leading decoration, to preserve reading space. Uses existing Material 1.4 components without a dependency upgrade. |

| Editor presentation source | Application |
| --- | --- |
| M3 containment / full-screen dialogs | Close navigation, explicit Save action, keyboard space, confirmation when discarding an edited draft; max 560dp on larger windows. |
| App design decision | Fixed primary Material tabs below the app bar switch between Edit and Preview. Both share the same draft and allow saving; Preview hides the keyboard and retains its scroll position. Formatting controls appear only in Edit. The source quote appears in Preview and the reader, preserving writing space in Edit. The editor uses native text-field scrolling inside its bounded remaining height; its accessible name remains available without a visible label. Formatting inserts Markdown without toggling existing delimiters. |
| Toolbar icons | Official Google Material icon vectors, source revision and Apache-2.0 attribution in each drawable. |
