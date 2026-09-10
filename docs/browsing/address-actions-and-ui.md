# Address actions and UI

## Cross-platform presentation

The cross-platform contract shares browser tabs, engine commands/events, menu availability and gesture
decisions in Kotlin. It also compiles the existing Candy production Compose surfaces directly for both
platforms: `BrowserMainMenu`, the Hero pager, `CompactTabGrid`, `CompactTabList` and the overview bottom
chrome live in `shared/src/commonMain`. Android and iOS call those same composables instead of maintaining
parallel menu or tab-overview renderers. Platforms supply only resources, native preview/image adapters,
haptics, blur/window effects and browser-engine hosting at the boundary.

Address loading feedback follows the same ownership. `AddressLoadCapsuleFeedback` and its rotating Candy
rainbow live in `commonMain`; `AddressBarMorphRules` supplies the same resisted scale and circular-corner
math to Android and iOS. Platform address chrome supplies geometry and progress state, not another loader.
On iOS 26+, the platform effect seam applies native glass to the complete address capsule and menu while
their content, section order and actions remain shared. The address content leaves the shared morph before
the menu settles, so the old bottom chrome cannot show through the glass panel.
The same effect seam supplies additive iOS presentation tokens: Apple-style type scale and semantic colors,
a compact 44-point control rhythm, horizontal overflow glyph, subtle tab-count treatment and continuous
grouped menu rows. Android keeps the existing Candy Material presentation; neither platform forks the menu
or address component tree.
Android renders page-specific binary menu actions as compact Material 3 Expressive tonal toggle
buttons: unselected controls use the round `secondaryContainer` treatment, selected controls morph
to a 12dp rounded-square `secondary` treatment, and pressed controls use the shared 8dp shape. All
colors resolve from `MaterialTheme.colorScheme`, so the saved Candy or Neutral palette and Android
dynamic device colors continue to follow the appearance setting. iOS keeps its native-style switch
presentation through the shared menu effect seam.

## Address flow

| Concern | Source | Rule |
| --- | --- | --- |
| Submission | [`AddressSubmissionRules.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/commands/AddressSubmissionRules.kt) | Highlighted suggestion wins; explicit `>` query never falls through to navigation |
| AI search mode | [`google-ai-mode.md`](google-ai-mode.md), [`AddressAiModeRules.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/commands/AddressAiModeRules.kt), [`SearchEngine.kt`](../../shared/src/commonMain/kotlin/dev/sk2andy/materialbrowser/browser/SearchEngine.kt) | The opt-in logo appears only for supported engines and real search input. Google AI queries use the provider's official `/ai?q=` entry and follow its current AI Mode redirect. Selected state lasts only for the current editor session; URLs and commands always keep their normal routing. |
| Commands | [`browser/commands/`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/commands/) | Build commands from current context, match deterministically, dispatch through actions |
| Candy Recall | [`recall.md`](recall.md), [`RecallModels.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/recall/RecallModels.kt), [`RecallRepository.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/data/RecallRepository.kt) | When opted in, a regular-tab query with at least two meaningful words shows at most two active-profile local matches under **From your history**, before remote suggestions. `>recall <query>` searches only that local index. Recall is absent in private tabs. |
| Search suggestions | [`SearchSuggestionProvider.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/suggestions/SearchSuggestionProvider.kt), [`searxng.md`](searxng.md) | None, DuckDuckGo, Google, Brave, Ecosia, Qwant, Startpage, Kagi and SearXNG share bounded reads and provider-isolated caches. Every provider and fallback call stays disabled for private tabs. |
| History suggestions | `BrowserSessionStore`, `BrowserController`, `SearchSettingsPage` | Search settings can hide saved active-profile history rows, automatic Candy Recall results and history-derived domain completion. The enabled-by-default global preference does not hide matching open tabs, favorite-derived completion or an explicit `>recall` query. |
| Presentation | [`ui/AddressBarPresentationRules.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/AddressBarPresentationRules.kt), [`ui/AddressBarInsetRules.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/AddressBarInsetRules.kt) | Resolve UI mode with pure rules before composing; subtract any platform-applied IME resize before padding bottom chrome |
| Load feedback | [`AddressLoadCapsuleFeedback.kt`](../../shared/src/commonMain/kotlin/dev/sk2andy/materialbrowser/shared/ui/AddressLoadCapsuleFeedback.kt), [`AddressBarMorphRules.kt`](../../shared/src/commonMain/kotlin/dev/sk2andy/materialbrowser/shared/ui/AddressBarMorphRules.kt) | Shared resolver selects hidden, indeterminate, determinate or settling state. A closed rotating Candy rainbow follows the morphed capsule outline; invalid phases and geometry fail safe. |
| Blank-tab editor | `ui/NewTabPage.kt`, `ui/ExpandedAddressBar.kt` | Keep regular-tab favorites visible and actionable without a redundant section label while address input is focused; hide them in private mode. Keep the private-mode toggle immediately after the address input, including while the focused editor uses the full available width. |
| QR scanner | `ui/QrCodeScanner.kt`, `src/full/ui/QrCodeScanner.kt` | The `full` flavor delegates explicit scans to Google Code Scanner. The F-Droid `foss` flavor hides the action and contains no scanner SDK. |

Cache and cookie commands use the selected browser engine's data-clearing adapter. Gecko cache
clearing removes only all cache types; cookie clearing covers the process runtime shared by Candy's
regular profiles. Both commands stay pending until the engine reports completion and reload only the
unchanged originating tab. No Chromium/WebView fallback participates in either command.

## Configurable expanded actions

| Concern | Source | Rule |
| --- | --- | --- |
| Catalog and layout | [`AddressBarActionLayout.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/data/AddressBarActionLayout.kt) | The address field and trailing **More** action are fixed. Users may place at most three unique actions before or after the field, for at most four outer icons including **More**. The default remains **Tabs** before and **New tab** after the field. |
| Available actions | [`AddressBarActions.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/AddressBarActions.kt) | Favorite, pin, desktop view, forced vertical scrolling, Reader Studio, find in page, tabs, share, print, new tab, reload/stop, close tab, back, forward and right parking share the same runtime availability rules as their menu equivalents. Unavailable actions stay visible but disabled. |
| Editing | [`AddressBarActionEditor.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/AddressBarActionEditor.kt), [`AddressBarActionEditorRules.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/AddressBarActionEditorRules.kt) | Tabs & gestures opens a drag editor with a fixed preview and a scrollable available palette. A long press lifts an action through breakaway resistance. Slots are derived deterministically from measured action bounds and canonicalized against the layout with the dragged action removed, so its two raw neighbors become one plus-marked target at the vacated center. Other valid gaps remain gently wiggling, shape-morphing bubbles; the active target grows, settles to its static hit anchor and slot changes tick. A palette tile keeps its label while moving freely, then fades the label and springs to the 48dp toolbar size as it snaps toward a slot. Toolbar drops keep the real item hidden until its final post-layout bounds are measured, then spring the overlay directly to that button center before handoff. Returning an action expands it toward its measured palette tile while its label fades back in. Accepted drops confirm and rejected/full drops reject haptically. Moving an action removes it from its old location, so the palette and address bar cannot contain duplicates. Accessibility custom actions provide equivalent placement and removal. |
| Persistence and migration | [`BrowserSessionStore.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/data/BrowserSessionStore.kt) | Stable wire names persist the two ordered sides. Unknown, duplicate or excess values are normalized independently of Compose. The former tab-button visibility preference migrates once into the new layout and is then removed. |
| Temporary controls | `ui/BrowserBottomBar.kt`, `ui/ExpandedAddressBar.kt`, `ui/BrowserMainMenu.kt` | Cast and blank-editor controls consume the same icon budget. Cast may temporarily displace the last configured action; any displaced action that has no ordinary menu equivalent is exposed in **More** for that state. |

The parked compact pill remains intentionally action-free. When address input takes the full editor
width, configured actions retain the existing horizontal fade/shrink transition and return when the
editor closes.

## Find in page

| Layer | Source | Boundary |
| --- | --- | --- |
| State and rules | [`FindInPage.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/FindInPage.kt) | Query changes reset result state; match ordinals and counts are normalized without Android dependencies. |
| Android Gecko session | `browser/BrowserController.kt`, `browser/gecko/GeckoViewRuntimeHandle.kt` | Gecko's native finder is bound to the selected tab, exact session and navigation generation. Query, next/previous and dismissal all remain inside that session. |
| UI | [`FindInPageBar.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/FindInPageBar.kt), `ui/BrowserMainMenu.kt` | The main menu and configurable action open the same focused search bar. It reports the current/total match, disables navigation until matches exist and closes through Back or its close action. Find mode forwards IME insets to the browser viewport so native match navigation scrolls results above the keyboard. |

## Gestures and actions

| Interaction | Source | Boundary |
| --- | --- | --- |
| Horizontal tab switch | Android `AddressBarGestureRules` / `AddressBarTabSwitchRules`; shared `BrowserChromeGestureRules` | Pure distance/velocity decision; the platform controller changes selection |
| Upward overview morph | Android `AddressBarOverviewGestureRules` / `AddressBarMotion`; shared `BrowserChromeGestureRules` and `AddressBarMorphRules` | Shared resisted progress, scale and corner math keep the capsule circular while it morphs; platform chrome owns gesture execution and measured geometry |
| Vertical page scroll | [`BrowserChromeScrollRules.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/BrowserChromeScrollRules.kt) | GeckoView emits engine-neutral scroll positions through a latest-value dispatcher capped at 15 browser-chrome updates per second. The last processed position remains the accumulator baseline, so coalescing does not lose travelled distance. Downward travel compacts after 24dp, upward travel expands after 16dp, reaching the document top expands immediately, and direction changes reset accumulated travel. Only the selected tab's current renderer is accepted; background, replaced and closed sessions cannot mutate its pill state. Editing and tab overview continue to win through `AddressBarPresentationRules`, without a second renderer-specific address bar. |
| Address-bar parking | `AddressBarDockingRules`, `AddressBarMotion` | The existing park action creates an edge pill. Its single physical chevron points toward the last parked edge and sits on that same side of the centered address text. The parked pill can be dragged in two dimensions and snaps to the nearest physical edge at the released height. The normal-height anchor visibly stretches the pill under resistance, then releases it with a spring. Live movement haptics stop when movement pauses; edge snaps and anchor breakaway use confirm feedback. Parked, centered and overview positions share one spring path. |
| Configurable right parking | `AddressBarAction.ParkRight`, `BrowserController.parkAddressBarOnRight` | Keeps a right-park button in the configured address actions. It reuses the remembered vertical position, forces only the right edge, and leaves the resulting pill draggable. |
| Link Peek | [`LinkPeek.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/LinkPeek.kt), [`LinkPeekActionLayout.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/data/LinkPeekActionLayout.kt), `WebContentActionState`, `BrowserContentTargetRules` | Temporary Gecko preview with exactly four positions. Three configurable positions accept unique actions or remain empty; dragging an action back to the palette preserves its empty position. Only empty configurable positions are drop targets, so occupied actions and the pulsing background-tab `+` never show a drop marker. The `+` is fixed in the third, penultimate position. The catalog contains Reader offline, private-open, copy, share, favorite, snooze, and foreground-tab open. Every quick action uses the current committed HTTP(S) URL after redirects; explicit downloads retain the original target URL and source-tab session headers. Gecko `ContentDelegate.onContextMenu` crosses the normalized content-target boundary without exposing `GeckoSession` to action state. Switching tabs or starting a new source navigation invalidates the target, and only `+` owns commit motion. |
| External Link Preview | [`ExternalLinkPreviewBar.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/ExternalLinkPreviewBar.kt), [`ExternalLinkPreview.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/ExternalLinkPreview.kt) | When enabled in Browser settings, an accepted external `ACTION_VIEW` or web-link `ACTION_SEND` replaces the normal address bar with one compact bottom pill. Preview chrome is shown before engine preparation, so **Open in Candy** stays immediately available while content starts. Android uses a transient Gecko session with a `GeckoView`; once its blur host attaches, the pill uses that same backdrop and address-bar transparency tokens on first render and every appearance recomposition. Back consumes preview history before closing, the host represents the URL, the outlined split action opens in Candy or selects a regular local profile, and **More** contains share, copy, find-in-page, and desktop-site actions. The configurable address-action layout and docking do not apply to this temporary chrome. A user-driven departure from Candy discards the preview before the app can be reopened from Home, the app drawer, or Recents. |
| Long-press page content | [`BrowserContentActions.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/actions/BrowserContentActions.kt), [`BrowserContentTarget.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/actions/BrowserContentTarget.kt), [`LinkLongPressAction.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/actions/LinkLongPressAction.kt) | Normalize link/image URLs before dispatch and reject non-HTTP(S) payloads. Tabs & gestures selects Link Peek (default), copy, share, download, or an explicit regular/private foreground/background tab destination for link targets. Every dispatched choice emits one short haptic. Successful background creation uses the shared subtle address-bar pulse while the source tab stays selected. Existing stored regular-background and private-foreground choices retain their stable IDs; unsupported private opens fall back to Link Peek. Direct link downloads reuse the existing engine-scoped context-download path; Gecko retains its source referrer and private session context. Gecko image-link context elements retain both link and image actions, while image-only targets keep the content sheet and video/audio sources are not mislabeled as image downloads. Renderable `.txt` and `.json` links remain viewable on normal taps and can be sent explicitly to the configured download manager from Link Peek. |
| Share/download/assistant/external app | [`browser/integration/`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/integration/), [`browser/actions/`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/actions/), [`GeckoDownloadTransfer.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/gecko/GeckoDownloadTransfer.kt) | Construct bounded requests. Gecko context downloads stay in their profile/private request context and stream to MediaStore without exporting cookies; other Android actions use their focused adapters. |
| Page translation | [`page-translation.md`](page-translation.md), [`PageTranslation.kt`](../../shared/src/commonMain/kotlin/dev/sk2andy/materialbrowser/browser/PageTranslation.kt) | Validate and encode the current HTTP(S) URL with shared rules, then navigate the selected Android or iOS engine tab to one provider URL |

## Android keyboard and mouse input

`MainActivity` resolves global browser chords before the focused Compose or engine view receives
them. `BrowserHardwareInputRules` owns deterministic matching and cyclic active-profile tab
selection. Unmatched keys, primary/secondary clicks, hover, wheel/trackpad scrolling and webpage
text-editing commands continue through normal Compose and WebView/GeckoView dispatch.

Before an unmatched physical key reaches the window, Candy gives the selected attached engine view
focus when browser chrome does not own the IME. If that focus changes, the first key stroke is replayed
to the engine after the focus handoff, so the first letter is not lost. The address editor and
find-in-page bar retain IME ownership. Pointer-classified wheels keep normal Android hit-test dispatch.
For OEM and desktop-Android adapters that report a vertical wheel without the pointer source class,
Candy normalizes the report into a mouse-wheel event for the active engine. If an engine rejects that
event, its relative-scroll port remains the fallback.

| Input | Browser action |
| --- | --- |
| `Ctrl/Meta+L` | Leave a Site Capsule or external-link preview when necessary, then focus the address editor |
| `Ctrl/Meta+T` | Create and select a tab in the current regular/private mode, then focus the address editor |
| `Ctrl/Meta+W` | Close the selected deletable tab |
| `Ctrl/Meta+R`, `F5` | Reload the selected non-blank page |
| `Ctrl/Meta+F` | Open find-in-page for the regular browser or external-link preview |
| `Ctrl/Meta+Tab`, `Ctrl/Meta+Shift+Tab` | Select the next/previous active-profile tab with wrap-around |
| `Alt+Left`, `Alt+Right` | Traverse page history when that direction is available |
| Mouse Back, Mouse Forward | Traverse page history once whether Android reports `ACTION_BUTTON_PRESS` or a mouse-sourced Back/Forward key; matching dual reports are deduplicated |
| Right-click on page link/image | Open the same normalized content action as long-press; Gecko context menus and System WebView context clicks share the contract |

Recognized key-down events, repeats and their matching key-up are consumed as one shortcut. The
matcher rejects extra modifiers, so reserved text commands such as copy/paste/undo remain owned by
the focused field or webpage. App-owned onboarding, release notes, transfer and extension-manager
surfaces temporarily disable browser shortcut interception. Android's system keyboard-shortcut help
lists the primary Ctrl chords.

## UI source ownership

| Surface | Owner |
| --- | --- |
| Root state, effects, Back priority and surface routing | `ui/BrowserScreen.kt` |
| Address chrome orchestration and tab-swipe presentation | `ui/BrowserAddressChrome.kt` |
| Popup, federated-login and CAPTCHA offer feedback | `ui/BrowserOfferSnackbarEffects.kt` |
| Browser dialogs, Link Peek and content-action overlays | `ui/BrowserTransientOverlays.kt` |
| Android engine renderer and external-preview hosting | `ui/BrowserViewport.kt` |
| Link Peek action editor and drag policy | `ui/LinkPeekActionEditor.kt`, `ui/LinkPeekActionEditorRules.kt` |
| New-tab content | `ui/NewTabPage.kt` |
| Compact and docked address chrome | `ui/BrowserBottomBar.kt` |
| Expanded editor and actions | `ui/ExpandedAddressBar.kt` |
| Recall, navigation, search and command suggestions | `ui/AddressSuggestions.kt` |
| Shared main-menu presentation | `shared/src/commonMain/.../ui/BrowserMainMenu.kt`, with Android resources/effects wired by `ui/BrowserMainMenu.kt` |
| Shared tab-overview presentation | `shared/src/commonMain/.../ui/TabOverviewComponents.kt`, `CompactTabGrid.kt`, `CompactTabList.kt`, `TabOverviewChrome.kt` |
| Tab-actions presentation | `shared/src/commonMain/.../ui/TabActionsMenu.kt`; platform call-site cutover and iOS action-state wiring remain open |

External download routing supports the built-in downloader, per-download selection, or one persisted
verified manager. Verified external targets are 1DM, ADM, and Download Navi. ADM and Download Navi
receive only the normalized HTTP(S) URL and MIME type through an explicit `ACTION_VIEW` intent. 1DM
session sharing remains separately opt-in and is never allowed for private tabs. If a selected app is
missing or cannot be started, Candy falls back to the built-in downloader.

## Change pattern

1. Add or change deterministic behavior in a focused rule/model.
2. Cover it in `src/test`.
3. Wire controller state/actions.
4. Render and animate in focused Compose functions.
5. Add instrumentation only for Android, GeckoView, semantics, or gesture integration.

Address-bar parking is enabled by default under Tabs & gestures. Disabling it immediately restores
the centered pill, hides the built-in compact-pill park control, and prevents a persisted parked state from returning
after restart. Compact address text stays vertically centered whether the park action or Cast action
is present. Active docking and the last edge/height are stored separately: restoring or disabling the
pill centers it without forgetting where the next park action should place it. The normalized position
survives window-size changes and restart. Clicking a parked pill restores the expanded address bar
without focusing address input or opening the keyboard.
Dragging a parked pill into the 28-dp normal-address-bar zone now magnetically resolves its live
vertical position to the safe-area-adjusted anchor, emits one confirm haptic on entry, and persists
that exact anchor on drop. The same rule uses the post-inset travel distance, so navigation-bar and
IME resizing do not shift the physical threshold; unresolved zero-height layouts never force a snap.
Blank new tabs keep parking unavailable so address entry remains directly accessible.
The configurable **Park address pill right** action remains in the saved layout when unavailable and
becomes enabled again on a non-blank page. It does not resize or inset the engine renderer.

Remote search suggestions keep their saved provider across distributions. On a new installation,
`full` defaults to DuckDuckGo while `foss` defaults to `None`; this prevents address text from
leaving a new F-Droid installation until the user explicitly enables a provider.
Google suggestions use the current public HTTPS OpenSearch-style endpoint with explicit UTF-8 input
and output parameters. Google documents this feed as unpublished and unsupported, so availability
is not guaranteed. Like every remote provider, Google receives regular-tab input only after the
minimum query length and only after explicit provider selection.
