# Candy iOS host

This target is a thin Apple host around a shared Compose Multiplatform browser root.
Browser-neutral URL/navigation, engine commands/events, tab intents and chrome gestures live in
`../shared`. The existing Candy production `BrowserMainMenu`, Hero pager, `CompactTabGrid`,
`CompactTabList` and overview bottom chrome have also been physically moved there and compile for both
Android and iOS. Apple owns lifecycle, lazily attached `WKWebView` sessions, native image/effect adapters and mandatory
system presenters. `CandyComposeHost` mounts the shared `ComposeUIViewController` and overlays the native
address/menu chrome, Find, Share and error presentation; Favorites, History, Reader and Candy Trails
render through shared Compose surfaces. The iOS address bar and optional floating menu use native Liquid
Glass; their state, item ordering, enabled/checked values and actions come from Kotlin. The default
bottom-sheet menu reuses Android's common Compose content. The host does not duplicate overview,
Settings, library, Reader or Trail screens.
`WKWebViewBrowserSessionAdapter` is the only engine boundary: it executes shared
commands and reports WebKit navigation callbacks as shared events. The shared controller
therefore manages sessions without knowing WebKit.

## Shared Apple presentation

| Surface | Shared anatomy and Apple adapter |
| --- | --- |
| Settings | Shared routes and rows with grouped semantic colors, Apple typography, compact icon tiles and native `UISwitch`/`UISlider` visuals. Player, Downloads and Site Capsules entries are hidden on iOS. Shared row semantics own toggle input; the shared slider retains snapping, gestures, progress accessibility and completion callbacks while UIKit draws its track/thumb. Native decorative controls do not create duplicate accessibility elements. Android retains its Material control defaults. |
| Overview and Reader chrome | Same shared floating controls, backed by `UIVisualEffectView` and `UIGlassEffect` on iOS 26; earlier versions use system material. Native SF Symbols replace Android vectors through an alpha-preserving bitmap presentation adapter, with vector fallback for unavailable symbols. The native address/menu backdrop uses regular Liquid Glass for legibility over both bright and dark website content. |
| Library | One shared search-state/renderer contract serves Android's production Material SearchBar and the iOS rounded filled field. The Android renderer stays in the app module so it compiles against the app's resolved Material API; this avoids Compose Multiplatform/Android Material SearchBar binary incompatibility. Library header, grouped rows and icons adapt through the same presentation seam. |
| Reader and profile sheets | Shared modal content uses Apple glass surfaces, compact handles and native-style actions; Reader font/theme/alignment selections use the same shared choice model with Apple segmented styling. Android retains its Material sheets and controls. |
| Appearance | Persisted System/Light/Dark choices update shared screens and native chrome. User animation preferences combine with system Reduce Motion. The shared size control is labeled “Seitengröße” on iOS and applies native `WKWebView.pageZoom` to existing and newly attached sessions; it scales all page content. Android retains its full form, text sizing, palettes, shapes and effects. Unsupported Apple chrome options are omitted. |
| Web viewport | One coordinator-owned UIKit container hosts the selected engine view. Snapshot updates keep the same Compose interop holder; native child layout follows container bounds. `UIKitView` uses direct native interaction and WebKit accessibility. Native equality guards prevent Swift/Objective-C wrapper conversions from remounting the same viewport. |
| Accessibility | Changes to Reduce Motion and Reduce Transparency are observed during composition; hero travel, Settings navigation and Reader resizing stop under Reduce Motion; glass becomes an opaque semantic surface under Reduce Transparency |

Apple API references: [UIGlassEffect](https://developer.apple.com/documentation/uikit/uiglasseffect),
[UISwitch](https://developer.apple.com/documentation/uikit/uiswitch),
[UISlider](https://developer.apple.com/documentation/uikit/uislider),
[Reduce Transparency](https://developer.apple.com/documentation/uikit/uiaccessibility/isreducetransparencyenabled),
[Reduce Motion changes](https://developer.apple.com/documentation/uikit/uiaccessibility/reducemotionstatusdidchangenotification).

## Website top safe area

| Concern | Owner and invariant |
| --- | --- |
| Status bar / Dynamic Island | `BrowserViewportContainer` measures physical overlap with the window safe-area frame and applies that top inset to WebKit content and the vertical scroll indicator |
| Double inset prevention | UIKit automatic content/indicator adjustment is disabled; an already-safe viewport receives zero additional top inset. On iOS 26+, public `WKWebView.obscuredContentInsets.top` reports the consumed area to WebKit, which positions fixed/sticky content and subtracts it from CSS `env(safe-area-inset-top)` for `viewport-fit=cover` pages |
| Earlier iOS versions | iOS 18.4–18.x retains the explicit scroll inset fallback; its public WebKit API cannot report an obscured layout area, so cover pages that add CSS safe-area padding may still receive duplicate top spacing |
| Scroll restoration | Only a viewport already at its previous top anchor moves to the new top; restored or scrolled reading positions remain unchanged |
| Shared overview | The WebView keeps its full frame/bounds, so shared hero geometry and native snapshot dimensions stay unchanged |
| Layout stability | Insets are assigned only when changed and recursive inset updates are guarded; existing bottom/side inset values are retained |

Run the deterministic geometry/scroll-anchor checks without a simulator:

```sh
xcrun swiftc iosApp/CandyIos/BrowserViewportInsetsRules.swift \
  iosApp/Tests/BrowserViewportInsetsRulesTests.swift -o /tmp/candy-ios-viewport-insets-tests
/tmp/candy-ios-viewport-insets-tests
```

## Tab previews

Each iOS tab keeps at most one native `UIImage` snapshot in
`BrowserTabSnapshotStore`. The store is memory-only: it never writes previews to
disk or preferences, so private tabs cannot leave persisted previews.
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
header. The selected mode, compact-overview bottom anchoring and 10–90% tab-dismiss resistance
(default 40%) are stored in `UserDefaults`; snapshots remain memory-only. Missing or unknown stored values safely fall back to
Hero with bottom anchoring disabled.

## Remaining visual differences from Android

| Area | iOS difference |
| --- | --- |
| Overview orchestration | Shared production components, entry/exit hero motion, profile switcher and profile icon/isolation sheets are active. Profile identity, active-profile selection, remembered tab ownership, icon and isolation mutations and count are owned by shared Kotlin. The Apple host persists the local profile ID/icon/isolation envelope. Shared close-all, move-to-profile and safe profile deletion are connected; profile reorder remains open. |
| Artwork adapters | Native favicon and incognito artwork are not connected; fallback vectors remain visible |
| Tab Actions | Production menu and action-state call site are shared; unsupported backend actions remain disabled |
| Settings destinations | Shared production home/routing connects Search provider/SearXNG, Appearance, Tabs & Gestures, Browser translation, Sync and Toppings state. Appearance exposes supported Apple controls only; other settings without native backends stay visibly disabled. Toppings list/add/edit/toggle/delete call the native validated runtime. |
| Feature actions | Favorites and active-profile History use the shared searchable library and protected atomic storage; shared Snooze presets/management are connected; Capsules, advanced library features and other missing backends remain unavailable |
| Native sheets | Find remains Apple-native; Reader uses shared content/library/settings plus native `AVSpeechSynthesizer` behind the common speech state machine |

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
The built-in Candy profile restores its icon and isolation through shared update methods before
any WebKit session attaches; it is not recreated or silently reset to the shared default store.
Isolation changes retire affected sessions with callbacks disconnected before replacement. Topping
command notifications refresh menu projection only, including notifications emitted during engine
construction; they never recursively start session reconciliation.

An isolated local iOS profile binds every regular tab to one persistent named `WKWebsiteDataStore`, derived from
the stable UUID profile ID. This separates cookies, sign-ins, site data, cache and service workers. Non-isolated
profiles share WebKit's default store; private or ephemeral sessions always use a non-persistent store. Changing
isolation recreates only that profile's WebKit sessions while preserving the shared tab records and URLs.
Moving a tab retires its old engine before attaching it to the destination store. Deletion switches to an
eligible local fallback and waits for its viewport to mount before removing an isolated profile's named
store. Open and snoozed tabs move through shared rules; source-profile History and desktop preferences
are removed. If store removal fails, the profile metadata stays available for retry. Deleting a
non-isolated profile never clears the default store shared with other profiles.

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
forward, reload/stop, favorite/pin, new tab, duplicate tab, close tab and tabs live in the more menu
projected from shared `BrowserFeatureMenuRules`. Both the count button and a dominant 56-point upward drag open the same
overview. A horizontal address-bar swipe selects the adjacent tab after 24% viewport
travel, or after 24 points at 900 points/second. Address editing suppresses both gestures.
WebKit scroll offsets feed the same `BrowserChromeScrollRules` used by Android: 24 points downward
compact the pill, 16 points upward or the document top expand it. Insets are included in the native
document offset; only the selected tab's current session can update chrome. Navigation and renderer
replacement reset its scroll baseline. WebKit retains its own scroll delegate.
The compact pill parks through its physical-side chevron; the menu also exposes parking. A parked
52-by-48-point handle restores the expanded bar without opening the keyboard. Holding for 320 ms
enables two-dimensional movement with the shared Android resistance, breakaway and edge/height snap
rules; a quick upward swipe opens the overview instead. Active parking and the remembered physical
edge/normalized height persist separately as global chrome preferences. Blank tabs, editing and menu
presentation keep the full address entry available. Reduce Motion disables the settling springs.
The shared menu-layout model controls presentation and action placement on Apple too. **Tabs & gestures
→ Menu actions** selects Bottom sheet (default) or Normal menu, vertical scrolling or two swipeable
pages, and five independently configurable footer slots. Both platforms use
`BrowserMenuPresentationSettings` and `BrowserMenuBottomSheetContent`; iOS supplies its existing glass,
symbol and control adapters. The iOS sheet hides native browser chrome, keeps its address row/shortcuts
and footer fixed, dismisses before commands, and remains open for toggles. A changed selected tab or
opened overview dismisses it. The floating choice retains the native SwiftUI menu.
All menu options and entry locations persist through the same bounded stable-string map in
`BrowserMenuLayoutPreference`; shared rules reject malformed or unsupported choices. Private URLs and
dynamic Topping identifiers never enter this preference.

On iOS 26+, SwiftUI applies `.glassEffect(.regular.interactive(), in: chromeShape)` to the framed
address or menu content. The native material supplies adaptive foreground contrast over both bright
and dark web content; menu rows remain non-lazy and do not animate independently while scrolling.
Reduce Motion removes the spring; Reduce Transparency switches to an opaque semantic fallback.
iOS 18–25 use system ultra-thin material. SF Symbols, 44-point targets, semantic colors and native
switches provide Apple-oriented presentation while Android keeps Candy's Material chrome.

| iOS feature action | Shared behavior and native implementation |
| --- | --- |
| Favorite / pin | Shared URL identity, bounded favorites, pin ordering and close protection; protected atomic regular-session/library envelope survives restart. Private favorites/history/pins never enter this envelope. |
| Favorites / History library | Shared `BrowserLibraryScreen` and `LibrarySearchBar` state/renderer contract; search, navigation, row deletion, favorite undo and active-profile History clear confirmation |
| Find in page | Liquid-Glass search bar backed by `WKWebView.find` with previous/next/wrap; callbacks are bound to source tab and WebKit session |
| Reader | Bounded, document-bound WebKit extraction and shared renderer; common codec persists settings, progress and offline snapshots with original profile provenance and an 8-MiB eviction budget. Failed extraction can resume a matching saved article; the shared offline library remains reachable. Private Reader never reads or mutates this store. |
| Reader speech | Native `AVSpeechSynthesizer` executes play/pause/stop and reports UTF-16 ranges through common `ReaderSpeechRules`; stale speech callbacks are rejected |
| Desktop mode | Shared exact-host/profile preference policy; WebKit applies preferred desktop/mobile content mode on every main navigation. Private overrides are memory-only and isolated by tab. |
| Regular / private tabs | Shared session restore persists only regular local-profile records and resets engine history/loading flags; inactive restored tabs attach lazily. The native plus-button context menu opens regular or private tabs through the common controller. |
| Snooze | Android production preset dialog, management list/card, calendar/DST rules and due/mutation rules are physically shared. Regular active+snoozed records persist atomically; private/synced/protected sources are unavailable. Foreground timers and launch/resume restore due tabs; explicit Snooze requests OS notification authorization and schedules up to 60 upcoming eligible local reminders. Denying authorization still permits foreground/relaunch restoration. Protected-profile titles are excluded from notifications; rescheduling/deletion reconciles pending and stale delivered reminders. Notification taps queue until the browser is ready after cold launch. Native custom date/time picker supplies only the chosen instant. |
| Tab/profile management | Shared close-all preserves pinned tabs; move-to-profile preserves privacy and replaces a moved selected source tab safely. Profile deletion moves open+snoozed tabs to an eligible local fallback; last, synced, bound and protected profiles stay protected. Native isolation cleanup failure keeps metadata intact. |
| Basic website permissions and uploads | WebKit owns website and OS prompts for camera, microphone and foreground location; Info.plist supplies purpose descriptions. Omitting media/upload delegate overrides preserves prompting and Safari-style file/photo selection. No Candy auto-grant or second per-origin permission store is added. |
| Page JavaScript prompts | Shared prompt sanitizer; native alert/confirm/text presentation cancels on navigation, presenter dismissal and session close |
| Downloads | WebKit download actions, main-frame attachments and unsupported MIME responses stream through `WKDownload`, then export through Files. The same common filename sanitizer serves Android and iOS. Up to four transfers and 1 GiB per file; protected temporary files stay outside backups and are removed on export, cancellation, owner-session close/process termination or the next app start. Private downloads create no browser history/queue record; only an explicit Files export creates a user-owned copy. |
| Candy Trail | WebKit back/forward history is reconciled through shared Trail rules and rendered by the shared Candy Trail graph; state is session-memory-only on iOS |
| Translate | Shared `PageTranslationRules` build the URL for the persisted Browser-settings provider (Google by default on iOS) with the current language, then the selected `WKWebView` session navigates normally; no reader extraction or translation sheet is created |
| Share / external / print | `UIActivityViewController`, `UIApplication.open`, and `UIPrintInteractionController` with the web view formatter |

The installed iOS 26.2 SDK's `WKUIDelegate.h` documents default media-capture prompting and Safari-style
iOS upload selection when delegates are omitted. [Apple's media-capture contract](https://developer.apple.com/documentation/webkit/wkuidelegate/webview(_:requestmediacapturepermissionfor:initiatedbyframe:type:decisionhandler:))
and [WebKit's upload implementation](https://github.com/WebKit/WebKit/blob/main/Source/WebKit/UIProcess/ios/forms/WKFileUploadPanel.mm)
define that native behavior. Geolocation continues through WebKit's built-in OS and website prompts;
no private delegate API or JavaScript location replacement is used.

Actions without an iOS implementation are not rendered. These gaps remain explicit:

| Gap | Current boundary |
| --- | --- |
| Site Capsules | Capsule editor/persistence, scoped navigation and platform launch adapters are absent |
| Snooze background limits | OS reminders can appear while Candy is closed; tab restoration occurs on launch/resume or foreground timer. iOS Snooze undo and broader background execution are not connected. |
| Site controls | Cookie banner removal, forced scrolling, per-site zoom/safe area, popup controls and domain mute need native backends; exact-host desktop mode and global page size are connected |
| Download queue | Foreground WebKit-to-Files export is connected; persistent download history/queue, background transfer and resume are absent |
| Advanced permissions and authentication | Basic WebKit camera/microphone/location and file input use system prompts; Candy permission history/radar, custom per-origin controls and HTTP authentication presentation remain incomplete |
| Profiles and chrome | Profile reorder, manual tab reorder, profile protection/wallpapers, automatic control-occlusion parking and unsupported Settings bodies remain open. Manual address parking and scroll compaction use shared Kotlin rules. Apple Appearance covers theme, UI motion and global page size; website animation suppression, forced dark websites and custom Candy palettes/effects remain Android-only. |
| Advanced libraries | Favorite folders/import/reorder, Recall/history tools and durable Candy Trail storage remain Android-only implementations |
| Platform capabilities | Firefox WebExtensions remain Gecko-only; Android widgets, Cast/PiP and related system integrations require separate Apple adapters |


`ToppingDependencyResolver` resolves bounded `@require` and `@resource` declarations only during
explicit import/update, using public allowlisted HTTPS endpoints, bounded redirects, DNS/private-IP
checks and optional SHA-256. `ToppingInstaller` then installs the persisted local payload as a
frame-scoped `WKUserScript` in a per-Topping named `WKContentWorld`. Missing frame metadata and
all v1/v2 records migrate to top-frame-only execution. Explicit broader requests remain capped by
the user's persisted permission. A revision-scoped `WKScriptMessageHandlerWithReply` authorizes
each frame's own HTTP(S) URL and assigns a native document UUID before source execution. Menu
commands route back through that UUID and WebKit frame target; main navigation, `pagehide` and
invalid frame targets remove stale routes. Back/forward-cache restores obtain a fresh UUID and
re-register their live menu commands. Private and ephemeral preview sessions attach no scripts
or bridge. This is not a Safari Web Extension host and does not promise WebExtension API
compatibility beyond the documented Candy Topping contract.

Topping management is rendered by shared Compose, not SwiftUI. The Swift bridge publishes only small
ID/name/enabled/frame-scope records; full source is requested lazily for edit. Save preserves an existing Topping's
enabled state and narrowed frame allowance, while new Toppings start enabled with their declared allowance.
Store reads are cached for the launch/viewport path;
successful mutations reconcile the active WKWebView runtimes through the existing native adapter.

`CandyContentBlockerCompiler` emits one escaped host per WebKit rule and never uses regex
disjunction. It splits network assets into lists with at most 45,000 host rules and cosmetic assets
into 15,000-rule lists. WebKit forbids combining `if-domain` and `unless-domain`: unrelated cosmetic
exceptions are removed from already scoped rules, while an overlapping allow/pause exception drops
that scoped hide rule fail-open. Scoped allow rules are repeated because `ignore-previous-rules` is
local to one list. Compiled v2 lists are reused from `WKContentRuleListStore`; first external
navigation waits until every list is active. Shared session attachment marks a restored nonblank tab
as loading before issuing that deferred command, so the native loading chrome remains visible while
WebKit activates its content rule lists.
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

The standalone native gate has thirteen executables: eight existing platform contracts plus regular-browser
storage (19 assertions), Reader text storage (10 assertions), temporary download files (12 assertions),
viewport inset rules and local-profile restore routing (9 assertions). Shared behavior is exercised by common
Kotlin tests; native storage tests cover only atomic I/O, bounds, ordering, corruption and failures.

The shared `CandyIos` scheme also includes `CandyIosUITests`. Its self-contained loopback page exercises
real WebKit scroll compaction/expansion, parking, a half-second hold followed by cross-edge/height drag,
process-restart persistence, tap restoration without keyboard focus and blank-tab entry. It adds no
production test hooks. Set `CANDY_UI_SIMULATOR_UDID` to this session's dedicated simulator UUID and run:

```bash
xcodebuild -project iosApp/CandyIos.xcodeproj -scheme CandyIos -configuration Debug \
  -sdk iphonesimulator \
  -destination "platform=iOS Simulator,id=$CANDY_UI_SIMULATOR_UDID" \
  -parallel-testing-enabled NO -maximum-concurrent-test-simulator-destinations 1 \
  ARCHS=arm64 ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=NO test
```

The explicit build settings keep the simulator architecture consistent with the configured
`iosSimulatorArm64` Kotlin framework; a destination cannot be combined with the `-arch` option.

| Native contract | Sources |
| --- | --- |
| Snapshot stale-result acceptance | `BrowserTabSnapshotRules.swift`, `BrowserTabSnapshotRulesTests.swift` |
| Overview mode preferences | `BrowserTabOverviewMode.swift`, `BrowserTabOverviewModeTests.swift` |
| Bounded page extraction | `BrowserPageExtractionRules.swift`, `BrowserPageExtractionRulesTests.swift` |
| Topping values and frames | `ToppingValueRules.swift` / `ToppingFrameRules.swift` and matching tests |
| Translation provider | `BrowserTranslationProviderPreference.swift`, matching tests |
| Glass accessibility/motion | `LiquidGlassPresentationRules.swift`, matching tests |
| Menu preference encoding | `BrowserMenuLayoutPreference.swift`, matching tests |
| Regular browser storage | `BrowserLocalStateStore.swift`, `BrowserLocalStateStoreTests.swift` |
| Local-profile restore routing | `BrowserLocalProfileRestore.swift`, `BrowserLocalProfileRestoreTests.swift` |
| Web viewport insets | `BrowserViewportInsetsRules.swift`, `BrowserViewportInsetsRulesTests.swift` |
| Reader text storage | `BrowserReaderLibraryStore.swift`, `BrowserReaderLibraryStoreTests.swift` |
| Temporary download files | `BrowserDownloadFileStore.swift`, `BrowserDownloadFileStoreTests.swift` |

Run from the repository root:

```bash
set -e
xcrun swiftc iosApp/CandyIos/BrowserTabSnapshotRules.swift iosApp/Tests/BrowserTabSnapshotRulesTests.swift -o /tmp/candy-ios-snapshot-rules-tests
/tmp/candy-ios-snapshot-rules-tests
xcrun swiftc iosApp/CandyIos/BrowserTabOverviewMode.swift iosApp/Tests/BrowserTabOverviewModeTests.swift -o /tmp/candy-ios-overview-mode-tests
/tmp/candy-ios-overview-mode-tests
xcrun swiftc iosApp/CandyIos/BrowserPageExtractionRules.swift iosApp/Tests/BrowserPageExtractionRulesTests.swift -o /tmp/candy-ios-page-extraction-tests
/tmp/candy-ios-page-extraction-tests
xcrun swiftc iosApp/CandyIos/ToppingValueRules.swift iosApp/Tests/ToppingValueRulesTests.swift -o /tmp/candy-ios-topping-value-tests
/tmp/candy-ios-topping-value-tests
xcrun swiftc iosApp/CandyIos/ToppingFrameRules.swift iosApp/Tests/ToppingFrameRulesTests.swift -o /tmp/candy-ios-topping-frame-tests
/tmp/candy-ios-topping-frame-tests
xcrun swiftc iosApp/CandyIos/BrowserTranslationProviderPreference.swift iosApp/Tests/BrowserTranslationProviderPreferenceTests.swift -o /tmp/candy-ios-translation-provider-tests
/tmp/candy-ios-translation-provider-tests
xcrun swiftc iosApp/CandyIos/LiquidGlassPresentationRules.swift iosApp/Tests/LiquidGlassPresentationRulesTests.swift -o /tmp/candy-ios-liquid-glass-tests
/tmp/candy-ios-liquid-glass-tests
xcrun swiftc iosApp/CandyIos/BrowserMenuLayoutPreference.swift iosApp/Tests/BrowserMenuLayoutPreferenceTests.swift -o /tmp/candy-ios-menu-layout-preference-tests
/tmp/candy-ios-menu-layout-preference-tests
xcrun swiftc iosApp/CandyIos/BrowserLocalProfileRestore.swift iosApp/Tests/BrowserLocalProfileRestoreTests.swift -o /tmp/candy-ios-profile-restore-tests
/tmp/candy-ios-profile-restore-tests
xcrun swiftc iosApp/CandyIos/BrowserViewportInsetsRules.swift iosApp/Tests/BrowserViewportInsetsRulesTests.swift -o /tmp/candy-ios-viewport-insets-tests
/tmp/candy-ios-viewport-insets-tests
xcrun swiftc iosApp/CandyIos/BrowserLocalStateStore.swift iosApp/Tests/BrowserLocalStateStoreTests.swift -o /tmp/candy-ios-local-state-tests
/tmp/candy-ios-local-state-tests
xcrun swiftc iosApp/CandyIos/BrowserLocalStateStore.swift iosApp/CandyIos/BrowserReaderLibraryStore.swift iosApp/Tests/BrowserReaderLibraryStoreTests.swift -o /tmp/candy-ios-reader-library-tests
/tmp/candy-ios-reader-library-tests
xcrun swiftc iosApp/CandyIos/BrowserDownloadFileStore.swift iosApp/Tests/BrowserDownloadFileStoreTests.swift -o /tmp/candy-ios-download-file-store-tests
/tmp/candy-ios-download-file-store-tests
```


## Native address-pill UI regression

`CandyIosUITests` is an XCUITest bundle in the shared `CandyIos` scheme. Its address-pill test
serves a static tall page through an ephemeral loopback-only `NWListener` in the test runner;
it needs no external fixture server or test-only app routes. It exercises real WebKit scrolling,
compact presentation, parking, a 0.5-second hold-and-drag across edges and height, persistence after
process relaunch, tap restoration without opening the keyboard, and usable blank-tab address entry.
The existing standalone tests remain separate from this UI bundle.

Run against an explicit simulator owned by the current session, with concurrent simulator testing
disabled. Supply that simulator's UUID through `CANDY_IOS_UI_SIMULATOR_UDID`; the simulator must be
available to Xcode's default device set. Use a fresh result-bundle path for every run:

```bash
xcodebuild -project iosApp/CandyIos.xcodeproj -scheme CandyIos \
  -configuration Debug -sdk iphonesimulator \
  -destination "platform=iOS Simulator,id=$CANDY_IOS_UI_SIMULATOR_UDID" \
  -parallel-testing-enabled NO -maximum-concurrent-test-simulator-destinations 1 \
  -only-testing:CandyIosUITests/CandyBrowserChromeUITests/testScrollParkRepositionRestoreAndBlankEntry \
  -resultBundlePath /tmp/candy-ios-chrome-ui.xcresult \
  CODE_SIGNING_ALLOWED=NO test
```
