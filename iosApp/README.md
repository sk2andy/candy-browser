# Candy iOS scaffold

This target is a thin Apple host around a shared Compose Multiplatform browser root.
Browser-neutral URL/navigation, engine commands/events, tab intents and chrome gestures live in
`../shared`. The existing Candy production `BrowserMainMenu`, Hero pager, `CompactTabGrid`,
`CompactTabList` and overview bottom chrome have also been physically moved there and compile for both
Android and iOS. Apple owns lifecycle, one `WKWebView` per tab, native image/effect adapters and mandatory
system presenters. `CandyComposeHost` mounts the shared `ComposeUIViewController` and overlays the native
address/menu chrome, Find,
Share, Favorites and error presentation; Reader and Candy Trails render through the same shared Compose surfaces as
Android. The iOS address bar and menu are deliberately native presentation duplicates because Liquid
Glass is central to the product; their state, item ordering, enabled/checked values and actions still come
from Kotlin. The host does not duplicate overview, Settings, Reader or Trail screens.
`WKWebViewBrowserSessionAdapter` is the only engine boundary: it executes shared
commands and reports WebKit navigation callbacks as shared events. The shared controller
therefore manages sessions without knowing WebKit.

## Tab previews

Each iOS tab keeps at most one native `UIImage` snapshot in
`BrowserTabSnapshotStore`. The store is memory-only: it never writes previews to
disk or preferences, so a future private tab cannot leave a persisted preview.
Capture runs after a committed navigation and before leaving the active tab or
opening the overview. A result is accepted only while all five identities still
match: tab ID, `WKWebView` session ID, navigation revision, latest request ID and
current page URL. The URL identity also rejects a late snapshot after a same-document
or SPA navigation.
Closing a tab, replacing its web view or starting another navigation invalidates
the pending result. A navigation keeps the previous accepted image visible until a newer capture
succeeds. Opening the overview waits for the visible WebKit capture before detaching the viewport.

Accepted `UIImage` snapshots cross the `iosMain` UIKit adapter and render inside the same shared Hero and
Grid preview slots used by Android's production overview. The adapter stores the `UIImage`, not a
single-parent `UIImageView`; every simultaneous hero/card composition receives its own image view.
Hero sizing, coverflow, dismissal physics,
Grid geometry and List rows therefore come from the physically shared production composables and their
shared rule objects, not a second iOS lazy-list implementation. Snapshot acceptance remains guarded by
the native identity rules above; UIKit image handles do not enter `commonMain`.

Hero, grid and list can be changed through the visible switcher in the overview
header. Only that presentation preference is stored in `UserDefaults`; snapshots
remain memory-only. Missing or unknown stored values safely fall back to Hero.

## Remaining visual differences from Android

| Area | iOS difference |
| --- | --- |
| Overview orchestration | Shared production components, entry/exit hero motion, profile switcher and profile icon/isolation sheets are active. Profile identity, active-profile selection, remembered tab ownership, icon and isolation mutations and count are owned by shared Kotlin. The Apple host persists the local profile ID/icon/isolation envelope; reorder state remains open. |
| Artwork adapters | Native favicon and incognito artwork are not connected; fallback vectors remain visible |
| Tab Actions | Production menu and action-state call site are shared; unsupported backend actions remain disabled |
| Settings destinations | `OpenSettings` opens shared production home/routing plus Appearance, Tabs & Gestures, Browser and Toppings pages; Toppings list/add/edit/toggle/delete call the native validated runtime, while controls without iOS backend state stay visibly disabled |
| Feature actions | Favorites opens a native searchable session library with per-row removal and timed undo; history, snooze and other Android-only feature implementations must be added before their menu actions can be enabled on iOS |
| Native sheets | Find remains Apple-native; Reader uses the shared Candy Reader surface, with iOS speech currently unavailable |

These are orchestration, adapter and feature-implementation gaps, not permission to create parallel tab,
Settings, Reader or Trail renderers. Tab IDs, engine sessions, selection, close behavior, overview presentation and
stale-result rules keep the shared ownership boundaries.

## Profiles

The iOS host does not define a second profile model. `BrowserProfile`, `BrowserTabState.profileId`,
`BrowserTabsState.activeProfileId`, profile creation, selection, remembered tab selection and active-profile
tab counts come from the shared Kotlin session controller. `CandyComposeHost` only maps canonical profile
presentation fields into the shared `ProfileSwitcher`, including synced display/icon metadata and the sync-linked
badge. The Android profile picker/actions implementation is physically shared; Android supplies localized resources
and iOS supplies the same canonical icon catalog plus platform symbol rendering. Local ID, icon and isolation values
survive an iOS process restart through a bounded `UserDefaults` adapter; invalid,
duplicate and over-limit entries are rejected again by the shared controller while restoring.

An isolated local iOS profile binds every regular tab to one persistent named `WKWebsiteDataStore`, derived from
the stable UUID profile ID. This separates cookies, sign-ins, site data, cache and service workers. Non-isolated
profiles share WebKit's default store; private or ephemeral sessions always use a non-persistent store. Changing
isolation recreates only that profile's WebKit sessions while preserving the shared tab records and URLs.

Candy Sync's repository, protocol codecs, mutation rules, durable-outbox policy, settings model and Settings UI
live in shared Kotlin and are used by both platforms. The iOS host contributes only the required Apple edges:
bounded `URLSession`/WebSocket transport, CryptoKit primitives, SwiftArgon2 recovery derivation, Keychain vault,
encrypted atomic cache and serial dispatch. The selected local profile is linked to this device and reconciled
through the same `SyncedProfileRuntimeRules` used on Android. Other devices appear as canonical synced profiles;
their tabs use stable, isolated named `WKWebsiteDataStore` instances. Private and non-HTTP(S) tabs never enter
the encrypted outbox. Endpoint, account, local-profile binding, device icon/accent, enrollment and manual refresh
are available on the shared Sync Settings page; server and E2EE passwords are never persisted.
Native ATS permits the self-hosted protocol's explicit HTTP compatibility mode, but the transport still sends
no Basic or bearer credential until unauthenticated discovery returns `allowHttp: true`; HTTPS remains the default.

The iOS-native bottom Candy chrome is `tab count | address | plus | more`, backed by the shared Kotlin
snapshot and action sink. Back,
forward, reload/stop, favorite/pin, new tab, close tab and tabs live in the more menu
projected from shared `BrowserFeatureMenuRules`. Both the count button and a dominant 56-point upward drag open the same
overview. A horizontal address-bar swipe selects the adjacent tab after 24% viewport
travel, or after 24 points at 900 points/second. Address editing suppresses both gestures.

On iOS 26+, one stable native `UIGlassEffect(style: .clear)` backdrop changes geometry between the
address bar and menu. The menu's scroll content is a non-lazy, animation-free foreground above that
backdrop, avoiding effect invalidation and flicker while scrolling. A light semantic tint preserves
text contrast; the refractive material fades toward the left/right lens edges to prevent mirrored web
text. Reduce Motion removes the spring; Reduce Transparency switches to an opaque semantic fallback.
iOS 18–25 use system ultra-thin material. SF Symbols, 44-point targets, semantic colors and native
switches provide Apple-oriented presentation while Android keeps Candy's Material chrome.

| iOS feature action | Native implementation |
| --- | --- |
| Favorite / pin | In-memory URL favorite and per-tab pin state; dynamic label plus badges in every overview mode; pin disables/hides close until unpinned |
| Favorites library | Native searchable list of every in-memory favorite; selection navigates the current tab and closes the library, while row deletion offers a five-second undo banner |
| Find in page | Liquid-Glass search bar backed by `WKWebView.find` with previous/next/wrap; callbacks are bound to source tab and WebKit session |
| Reader | JavaScript-bounded `article`/`main`/body extraction, accepted only for the initiating tab/session/URL/request, mapped into the shared Candy Reader model and renderer |
| Candy Trail | WebKit back/forward history is reconciled through shared Trail rules and rendered by the shared Candy Trail graph; state is session-memory-only on iOS |
| Translate | Shared `PageTranslationRules` build the URL for the persisted Browser-settings provider (Google by default on iOS) with the current language, then the selected `WKWebView` session navigates normally; no reader extraction or translation sheet is created |
| Share / external / print | `UIActivityViewController`, `UIApplication.open`, and `UIPrintInteractionController` with the web view formatter |

Actions without an iOS implementation are not rendered. Current explicit gaps are
address-bar docking, site toggles, Site Capsule,
summarization, snoozing, snoozed tabs, history and settings destination bodies. Firefox Extensions remains Android-only. Favorite
and pin state are currently session-memory only; persistence and pinned-tab ordering are
still future storage work.

`ToppingDependencyResolver` resolves bounded `@require` and `@resource` declarations only during
explicit import/update, using public allowlisted HTTPS endpoints, bounded redirects, DNS/private-IP
checks and optional SHA-256. `ToppingInstaller` then installs the persisted local payload as a
top-frame-only `WKUserScript` in a per-Topping named `WKContentWorld`. A revision-scoped
`WKScriptMessageHandlerWithReply` authorizes the live URL before source execution and implements
bounded, grant-checked GM values, dynamic menu commands and `GM_openInTab`; private tabs attach no
scripts or bridge. This is not a Safari Web Extension host and does not promise WebExtension API
compatibility beyond the documented Candy Topping contract.

Topping management is rendered by shared Compose, not SwiftUI. The Swift bridge publishes only small
ID/name/enabled records; full source is requested lazily for edit. Save preserves an existing Topping's
enabled state, while new Toppings start enabled. Store reads are cached for the launch/viewport path;
successful mutations reconcile the active WKWebView runtimes through the existing native adapter.

`CandyContentBlockerCompiler` emits one escaped host per WebKit rule and never uses regex
disjunction. It splits network assets into lists with at most 45,000 host rules and cosmetic assets
into 15,000-rule lists. WebKit forbids combining `if-domain` and `unless-domain`: unrelated cosmetic
exceptions are removed from already scoped rules, while an overlapping allow/pause exception drops
that scoped hide rule fail-open. Scoped allow rules are repeated because `ignore-previous-rules` is
local to one list. Compiled v2 lists are reused from `WKContentRuleListStore`; first external
navigation waits until every list is active.
WebKit cannot express EasyList-style `host.*` cosmetic scopes directly; those selectors currently
fail open on iOS and remain a documented blocking-parity gap.

Build the simulator app from the repository root. The target-based invocation avoids
Xcode consulting the global simulator device set while compiling:

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew \
  :shared:linkDebugFrameworkIosSimulatorArm64

ANDROID_HOME="$HOME/Library/Android/sdk" xcodebuild \
  -project iosApp/CandyIos.xcodeproj -target CandyIos \
  -configuration Debug -sdk iphonesimulator -arch arm64 \
  SYMROOT="$PWD/iosApp/xcode-build" \
  OBJROOT="$PWD/iosApp/xcode-obj" \
  CODE_SIGNING_ALLOWED=NO build
```

Boot, install and launch against an isolated simulator device set:

```bash
DEVICE_SET=/tmp/candy-browser-simulator-devices
DEVICE_ID="Candy Browser iPhone 17"
APP="$PWD/iosApp/xcode-build/Debug-iphonesimulator/CandyIos.app"

xcrun simctl --set "$DEVICE_SET" boot "$DEVICE_ID"
xcrun simctl --set "$DEVICE_SET" bootstatus "$DEVICE_ID" -b
codesign --force --sign - "$APP"
xcrun simctl --set "$DEVICE_SET" install "$DEVICE_ID" "$APP"
xcrun simctl --set "$DEVICE_SET" launch "$DEVICE_ID" \
  dev.sk2andy.candy.ios.scaffold
```

Run the platform-neutral stale-result contract with the installed Xcode Swift
toolchain:

```bash
xcrun swiftc \
  iosApp/CandyIos/BrowserTabSnapshotRules.swift \
  iosApp/Tests/BrowserTabSnapshotRulesTests.swift \
  -o /tmp/candy-ios-snapshot-rules-tests
/tmp/candy-ios-snapshot-rules-tests

xcrun swiftc \
  iosApp/CandyIos/BrowserTabOverviewMode.swift \
  iosApp/Tests/BrowserTabOverviewModeTests.swift \
  -o /tmp/candy-ios-overview-mode-tests
/tmp/candy-ios-overview-mode-tests

xcrun swiftc \
  iosApp/CandyIos/BrowserPageExtractionRules.swift \
  iosApp/Tests/BrowserPageExtractionRulesTests.swift \
  -o /tmp/candy-ios-page-extraction-tests
/tmp/candy-ios-page-extraction-tests

xcrun swiftc \
  iosApp/CandyIos/ToppingValueRules.swift \
  iosApp/Tests/ToppingValueRulesTests.swift \
  -o /tmp/candy-ios-topping-value-tests
/tmp/candy-ios-topping-value-tests

xcrun swiftc \
  iosApp/CandyIos/BrowserTranslationProviderPreference.swift \
  iosApp/Tests/BrowserTranslationProviderPreferenceTests.swift \
  -o /tmp/candy-ios-translation-provider-tests
/tmp/candy-ios-translation-provider-tests

xcrun swiftc \
  iosApp/CandyIos/LiquidGlassPresentationRules.swift \
  iosApp/Tests/LiquidGlassPresentationRulesTests.swift \
  -o /tmp/candy-ios-liquid-glass-tests
/tmp/candy-ios-liquid-glass-tests

xcrun swiftc \
  iosApp/CandyIos/BrowserFavoritesRules.swift \
  iosApp/Tests/BrowserFavoritesRulesTests.swift \
  -o /tmp/candy-ios-favorites-rules-tests
/tmp/candy-ios-favorites-rules-tests
```

The seven platform-edge executables above are the current iOS standalone test
gate. Run them all from the repository root with:

```bash
set -e
xcrun swiftc iosApp/CandyIos/BrowserTabSnapshotRules.swift iosApp/Tests/BrowserTabSnapshotRulesTests.swift -o /tmp/candy-ios-snapshot-rules-tests && /tmp/candy-ios-snapshot-rules-tests
xcrun swiftc iosApp/CandyIos/BrowserTabOverviewMode.swift iosApp/Tests/BrowserTabOverviewModeTests.swift -o /tmp/candy-ios-overview-mode-tests && /tmp/candy-ios-overview-mode-tests
xcrun swiftc iosApp/CandyIos/BrowserPageExtractionRules.swift iosApp/Tests/BrowserPageExtractionRulesTests.swift -o /tmp/candy-ios-page-extraction-tests && /tmp/candy-ios-page-extraction-tests
xcrun swiftc iosApp/CandyIos/ToppingValueRules.swift iosApp/Tests/ToppingValueRulesTests.swift -o /tmp/candy-ios-topping-value-tests && /tmp/candy-ios-topping-value-tests
xcrun swiftc iosApp/CandyIos/BrowserTranslationProviderPreference.swift iosApp/Tests/BrowserTranslationProviderPreferenceTests.swift -o /tmp/candy-ios-translation-provider-tests && /tmp/candy-ios-translation-provider-tests
xcrun swiftc iosApp/CandyIos/LiquidGlassPresentationRules.swift iosApp/Tests/LiquidGlassPresentationRulesTests.swift -o /tmp/candy-ios-liquid-glass-tests && /tmp/candy-ios-liquid-glass-tests
xcrun swiftc iosApp/CandyIos/BrowserFavoritesRules.swift iosApp/Tests/BrowserFavoritesRulesTests.swift -o /tmp/candy-ios-favorites-rules-tests && /tmp/candy-ios-favorites-rules-tests
```
