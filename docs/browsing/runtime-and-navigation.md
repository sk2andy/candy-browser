# Runtime and navigation

## Ownership

| Layer | Responsibility | Entry points |
| --- | --- | --- |
| Activity | Android lifecycle, incoming intents, permission/file chooser launchers, fullscreen web content, root theme | [`MainActivity.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/MainActivity.kt), [`FullscreenWebContentHost.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/FullscreenWebContentHost.kt) |
| Controller | WebView creation, tab/profile state, navigation, persistence coordination, platform callbacks | [`BrowserController.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/BrowserController.kt) |
| Compose | Render controller state and forward user actions | [`BrowserScreen.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/BrowserScreen.kt), [`StatusBarFrostedGlass.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/ui/StatusBarFrostedGlass.kt) |
| Policies | Resolve input, URLs, settings, media, file chooser and external routes | [`browser/`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/) |

## Navigation paths

| Input | Path | Boundary |
| --- | --- | --- |
| Address text | `AddressSubmissionRules` → `AddressResolver` → controller | Unknown input becomes HTTPS host navigation or selected-engine search |
| Android intent | `IncomingBrowserIntent` → controller | Accept normalized web URLs through shared URI policy |
| Special scheme | `BrowserUriPolicy` → `ExternalAppLauncher` | Block unsafe/internal schemes; require explicit external handling |
| Link Peek | `LinkPeekPreviewNavigationPolicy` → preview WebView | Keep only HTTP(S); do not hand off preview navigation |
| Site Capsule | `CapsuleIntentRules` → capsule runtime | Apply capsule-specific navigation boundary before normal routing |
| Desktop view | `DesktopSiteRules` → controller → WebView settings | Store registrable domains per profile; apply desktop user agent and viewport before navigation |

## Invariants

- Keep activity-result and lifecycle ownership in `MainActivity`; keep browser state in `BrowserController`.
- Show WebView custom views above browser chrome and enable sensor rotation for their lifetime.
  Web fullscreen takes orientation priority over the tab overview portrait lock; exiting restores
  the current browser orientation and system-bar policy.
- Route untrusted URLs through existing normalizers. Do not add a second permissive parser.
- Treat WebView callbacks as stale-capable: bind work to tab/request/navigation identity before applying results.
- Keep private tab state memory-only and skip remote suggestions for private input.
- Keep private desktop-view domains memory-only; persist regular domains per profile only.
- Apply desktop-view settings before main-frame navigation and reload matching open tabs when the domain preference changes.
- Keep browser content edge-to-edge. The top safe area redraws a blurred content layer with a
  surface-tinted fade so status-bar icons stay legible without adding WebView padding.
- Add pure policy beside the owning package; leave `BrowserController` as integration wiring.

## TLS trust channels

| Build | Trust anchors | Release asset |
| --- | --- | --- |
| Standard | Android system CA store | `CandyBrowser-v<version>-release.apk` |
| User CA | Android system and user CA stores | `CandyBrowser-v<version>-user-ca-release.apk` |

- Network Security Config is static and app-wide. Android WebView cannot safely switch trust anchors
  from a runtime preference, so broader trust requires installing the explicitly labeled User CA APK.
- Both channels use the same application ID and signing key. Update selection preserves the installed
  channel and rejects a release that contains only the other channel's asset.
- User CA trust applies to all app HTTPS connections, not only rendered pages or a selected profile.
  The settings warning must remain visible in User CA builds.
- `BrowserController.onReceivedSslError` always cancels. Never use `SslErrorHandler.proceed()` to
  approximate user-CA support; valid user-CA chains are accepted before that callback.

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
| TLS trust channels | `./gradlew testDebugUnitTest testUserCaDebugUnitTest assembleDebug assembleUserCaDebug`, then `python3 scripts/test_network_security_apks.py` |
