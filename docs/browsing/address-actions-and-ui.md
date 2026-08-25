# Address actions and UI

## Address flow

| Concern | Source | Rule |
| --- | --- | --- |
| Submission | [`AddressSubmissionRules.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/commands/AddressSubmissionRules.kt) | Highlighted suggestion wins; explicit `>` query never falls through to navigation |
| AI search mode | [`google-ai-mode.md`](google-ai-mode.md), [`AddressAiModeRules.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/commands/AddressAiModeRules.kt), [`SearchEngine.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/SearchEngine.kt) | The opt-in logo appears only for supported engines and real search input. Google AI queries use the provider's official `/ai?q=` entry and follow its current AI Mode redirect. Selected state lasts only for the current editor session; URLs and commands always keep their normal routing. |
| Commands | [`browser/commands/`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/commands/) | Build commands from current context, match deterministically, dispatch through actions |
| Search suggestions | [`SearchSuggestionProvider.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/suggestions/SearchSuggestionProvider.kt), [`searxng.md`](searxng.md) | Bound reads, isolate caches by provider configuration, and keep every provider and fallback call disabled for private tabs |
| Presentation | [`ui/AddressBarPresentationRules.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/AddressBarPresentationRules.kt), [`ui/AddressBarInsetRules.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/AddressBarInsetRules.kt) | Resolve UI mode with pure rules before composing; subtract any platform-applied IME resize before padding bottom chrome |
| Automatic parking | [`AddressBarParkingRules.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/AddressBarParkingRules.kt) | The global browser setting parks the selected address pill after each successful page load. The page menu can enable the same behavior per registrable domain and profile; private-domain choices remain memory-only. |
| Blank-tab editor | `ui/BrowserScreen.kt` | Keep regular-tab favorites visible and actionable while address input is focused; hide them in private mode |

## Gestures and actions

| Interaction | Source | Boundary |
| --- | --- | --- |
| Horizontal tab switch | `AddressBarGestureRules`, `AddressBarTabSwitchRules` | Pure distance/velocity decision; controller changes selection |
| Upward overview morph | `AddressBarOverviewGestureRules`, `AddressBarMotion` | Pure progress/motion math; Compose owns pointer input and animation |
| Link Peek | [`LinkPeek.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/LinkPeek.kt), `WebContentActionState` | Temporary preview; commit/open behavior remains explicit |
| Long-press page content | [`WebContentActions.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/actions/WebContentActions.kt) | Normalize link/image URLs before background open or download |
| Share/download/assistant/external app | [`browser/integration/`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/integration/), [`browser/actions/`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/actions/) | Construct bounded requests, then let Android adapters launch them |

## Change pattern

1. Add or change deterministic behavior in a focused rule/model.
2. Cover it in `src/test`.
3. Wire controller state/actions.
4. Render and animate in focused Compose functions.
5. Add instrumentation only for Android, WebView, semantics, or gesture integration.
