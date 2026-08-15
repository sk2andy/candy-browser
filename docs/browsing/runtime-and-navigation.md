# Runtime and navigation

## Ownership

| Layer | Responsibility | Entry points |
| --- | --- | --- |
| Activity | Android lifecycle, incoming intents, permission/file chooser launchers, root theme | [`MainActivity.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/MainActivity.kt) |
| Controller | WebView creation, tab/profile state, navigation, persistence coordination, platform callbacks | [`BrowserController.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/BrowserController.kt) |
| Compose | Render controller state and forward user actions | [`BrowserScreen.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/BrowserScreen.kt) |
| Policies | Resolve input, URLs, settings, media, file chooser and external routes | [`browser/`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/) |

## Navigation paths

| Input | Path | Boundary |
| --- | --- | --- |
| Address text | `AddressSubmissionRules` → `AddressResolver` → controller | Unknown input becomes HTTPS host navigation or selected-engine search |
| Android intent | `IncomingBrowserIntent` → controller | Accept normalized web URLs through shared URI policy |
| Special scheme | `BrowserUriPolicy` → `ExternalAppLauncher` | Block unsafe/internal schemes; require explicit external handling |
| Link Peek | `LinkPeekPreviewNavigationPolicy` → preview WebView | Keep only HTTP(S); do not hand off preview navigation |
| Site Capsule | `CapsuleIntentRules` → capsule runtime | Apply capsule-specific navigation boundary before normal routing |

## Invariants

- Keep activity-result and lifecycle ownership in `MainActivity`; keep browser state in `BrowserController`.
- Route untrusted URLs through existing normalizers. Do not add a second permissive parser.
- Treat WebView callbacks as stale-capable: bind work to tab/request/navigation identity before applying results.
- Keep private tab state memory-only and skip remote suggestions for private input.
- Add pure policy beside the owning package; leave `BrowserController` as integration wiring.

## Domain compatibility overrides

| Override | Runtime behavior |
| --- | --- |
| Force vertical scrolling | Removes vertical page scroll locks without changing horizontal overflow |
| Force page zooming | Removes viewport `user-scalable`, minimum-scale and maximum-scale restrictions while preserving other viewport directives |

- Compatibility overrides match the exact current host. Regular tabs persist them per profile;
  private tabs keep them in memory for that tab only.
- Changing an override reloads affected pages. Document-start scripts handle direct navigation and
  commit-visible fallbacks cover redirects whose final host was not known before navigation.

## Verification

| Change | Check |
| --- | --- |
| Input/URL policy | Matching JVM rule test |
| WebView settings or callbacks | Focused browser instrumented test |
| Android intent routing | Integration unit test plus launch instrumented test when lifecycle matters |
