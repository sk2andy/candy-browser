# Address actions and UI

## Address flow

| Concern | Source | Rule |
| --- | --- | --- |
| Submission | [`AddressSubmissionRules.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/commands/AddressSubmissionRules.kt) | Highlighted suggestion wins; explicit `>` query never falls through to navigation |
| AI search mode | [`google-ai-mode.md`](google-ai-mode.md), [`AddressAiModeRules.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/commands/AddressAiModeRules.kt), [`SearchEngine.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/SearchEngine.kt) | The opt-in logo appears only for supported engines and real search input. Google AI queries use the provider's official `/ai?q=` entry and follow its current AI Mode redirect. Selected state lasts only for the current editor session; URLs and commands always keep their normal routing. |
| Commands | [`browser/commands/`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/commands/) | Build commands from current context, match deterministically, dispatch through actions |
| Search suggestions | [`SearchSuggestionProvider.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/suggestions/SearchSuggestionProvider.kt), [`searxng.md`](searxng.md) | Bound reads, isolate caches by provider configuration, and keep every provider and fallback call disabled for private tabs |
| Presentation | [`ui/AddressBarPresentationRules.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/AddressBarPresentationRules.kt), [`ui/AddressBarInsetRules.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/AddressBarInsetRules.kt) | Resolve UI mode with pure rules before composing; subtract any platform-applied IME resize before padding bottom chrome |
| Blank-tab editor | `ui/BrowserScreen.kt` | Keep regular-tab favorites visible and actionable while address input is focused; hide them in private mode |
| QR scanner | `ui/QrCodeScanner.kt`, `src/full/ui/QrCodeScanner.kt` | The `full` flavor delegates explicit scans to Google Code Scanner. The F-Droid `foss` flavor hides the action and contains no scanner SDK. |

## Configurable expanded actions

| Concern | Source | Rule |
| --- | --- | --- |
| Catalog and layout | [`AddressBarActionLayout.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/data/AddressBarActionLayout.kt) | The address field and trailing **More** action are fixed. Users may place at most three unique actions before or after the field, for at most four outer icons including **More**. The default remains **Tabs** before and **New tab** after the field. |
| Available actions | [`AddressBarActions.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/AddressBarActions.kt) | Favorite, pin, desktop view, forced vertical scrolling, Reader Studio, find in page, tabs, share, print, new tab, reload/stop, close tab, back and forward share the same runtime availability rules as their menu equivalents. Unavailable actions stay visible but disabled. |
| Editing | [`AddressBarActionEditor.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/AddressBarActionEditor.kt), [`AddressBarActionEditorRules.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/AddressBarActionEditorRules.kt) | Tabs & gestures opens a drag editor with a fixed preview and a scrollable available palette. A long press lifts an action through breakaway resistance. Slots are derived deterministically from measured action bounds and canonicalized against the layout with the dragged action removed, so its two raw neighbors become one plus-marked target at the vacated center. Other valid gaps remain gently wiggling, shape-morphing bubbles; the active target grows, settles to its static hit anchor and slot changes tick. A palette tile keeps its label while moving freely, then fades the label and springs to the 48dp toolbar size as it snaps toward a slot. Toolbar drops keep the real item hidden until its final post-layout bounds are measured, then spring the overlay directly to that button center before handoff. Returning an action expands it toward its measured palette tile while its label fades back in. Accepted drops confirm and rejected/full drops reject haptically. Moving an action removes it from its old location, so the palette and address bar cannot contain duplicates. Accessibility custom actions provide equivalent placement and removal. |
| Persistence and migration | [`BrowserSessionStore.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/data/BrowserSessionStore.kt) | Stable wire names persist the two ordered sides. Unknown, duplicate or excess values are normalized independently of Compose. The former tab-button visibility preference migrates once into the new layout and is then removed. |
| Temporary controls | `ui/BrowserScreen.kt`, `ui/BrowserMainMenu.kt` | Cast and blank-editor controls consume the same icon budget. Cast may temporarily displace the last configured action; any displaced action that has no ordinary menu equivalent is exposed in **More** for that state. |

The parked compact pill remains intentionally action-free. When address input takes the full editor
width, configured actions retain the existing horizontal fade/shrink transition and return when the
editor closes.

## Find in page

| Layer | Source | Boundary |
| --- | --- | --- |
| State and rules | [`FindInPage.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/FindInPage.kt) | Query changes reset result state; match ordinals and counts are normalized without Android dependencies. |
| WebView session | `browser/BrowserController.kt` | Native `findAllAsync`, `findNext` and `clearMatches` calls are bound to the selected tab, exact WebView and navigation generation. Tab changes, navigation, close and controller destruction clear the listener and matches. |
| UI | [`FindInPageBar.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/FindInPageBar.kt), `ui/BrowserMainMenu.kt` | The main menu and configurable action open the same focused search bar. It reports the current/total match, disables navigation until matches exist and closes through Back or its close action. Find mode forwards IME insets to WebView so native match navigation scrolls results above the keyboard. |

## Gestures and actions

| Interaction | Source | Boundary |
| --- | --- | --- |
| Horizontal tab switch | `AddressBarGestureRules`, `AddressBarTabSwitchRules` | Pure distance/velocity decision; controller changes selection |
| Upward overview morph | `AddressBarOverviewGestureRules`, `AddressBarMotion` | Pure progress/motion math; Compose owns pointer input and animation |
| Address-bar parking | `AddressBarDockingRules`, `AddressBarMotion` | The existing park action creates an edge pill. Its single physical chevron points toward the last parked edge and sits on that same side of the centered address text. The parked pill can be dragged in two dimensions and snaps to the nearest physical edge at the released height. The normal-height anchor visibly stretches the pill under resistance, then releases it with a spring. Live movement haptics stop when movement pauses; edge snaps and anchor breakaway use confirm feedback. Parked, centered and overview positions share one spring path. |
| Link Peek | [`LinkPeek.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/LinkPeek.kt), `WebContentActionState` | Temporary preview; commit/open behavior remains explicit |
| Long-press page content | [`WebContentActions.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/actions/WebContentActions.kt) | Normalize link/image URLs before background open or download |
| Share/download/assistant/external app | [`browser/integration/`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/integration/), [`browser/actions/`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/actions/) | Construct bounded requests, then let Android adapters launch them |

## Change pattern

1. Add or change deterministic behavior in a focused rule/model.
2. Cover it in `src/test`.
3. Wire controller state/actions.
4. Render and animate in focused Compose functions.
5. Add instrumentation only for Android, WebView, semantics, or gesture integration.

Address-bar parking is enabled by default under Tabs & gestures. Disabling it immediately restores
the centered pill, hides the park action, and prevents a persisted parked state from returning
after restart. Active docking and the last edge/height are stored separately: restoring or disabling
the pill centers it without forgetting where the next park action should place it. The normalized
position survives window-size changes and restart. Clicking a parked pill restores it and focuses
address input. Blank new tabs keep parking unavailable so address entry remains directly accessible.

Remote search suggestions keep their saved provider across distributions. On a new installation,
`full` defaults to DuckDuckGo while `foss` defaults to `None`; this prevents address text from
leaving a new F-Droid installation until the user explicitly enables a provider.
