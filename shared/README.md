# Candy shared scaffold

This Gradle module is Candy's Kotlin Multiplatform boundary. The Android application consumes the
same module that is exported to iOS. It targets Android, iOS devices, and Apple-silicon simulators.

| Source | Responsibility |
| --- | --- |
| `commonMain/browser` | HTTP(S) and page-translation URL rules, tab/session reducers, durable session projection, bounded favorite/history library rules, feature-menu presentation, chrome gestures and exact tab-overview geometry |
| `commonMain/topping` | Topping metadata parsing and platform-neutral injection plans |
| `commonMain/data/DownloadFileNameRules` | Existing Android filename sanitization, MIME extension and truncation policy, also called by the native WebKit download adapter |
| `commonMain/ui` | Compose Multiplatform root plus production menu, tab overview, address-load/morph and settings core renderers |
| `commonMain/sync` | Candy Sync repository, protocol codecs, encrypted-outbox rules and native adapter contracts |
| `iosMain/ui` | `ComposeUIViewController` plus the native `WKWebView` viewport seam |
| `iosApp` | Thin SwiftUI system presenter, `WKWebView`, blocking and `WKUserScript` adapters |

`commonMain` deliberately contains no Android, WebKit, SwiftUI, storage, or native image types.
Compose Multiplatform 1.8.2 remains on the existing KMP Android plugin while GeckoView 155 requires
compileSdk 37.1 / AGP 9.4. Built-in Kotlin stays disabled as a temporary AGP 9 migration bridge.
`BrowserViewportSnapshot` and `BrowserViewportActionSink` are the strangler seam: Kotlin owns the
browser chrome state/actions and physically shared production overview, Reader, Trails and Settings.
Android renders the common chrome/menu composables; iOS reuses the common bottom-sheet menu content
and projects address/floating-menu state into native Liquid Glass while each platform embeds its engine view. The iOS app
converts a `ToppingInjectionPlan` into a main-frame-only `WKUserScript` in a named content world.
It does not host WebExtensions.

`BrowserSessionController` owns the `tab ID -> BrowserEngineSessionPort` registry and sends typed
`BrowserEngineCommand` values. Platform adapters own engine instances, execute those commands and
return `BrowserEngineEvent` values; shared Kotlin never imports `WKWebView`, `WebView` or GeckoView.
The nested `BrowserTabsController` remains the source of truth for tab identity, selection,
navigation state, overview visibility and address-focus requests. New tabs are blank, selected immediately
and request address focus; only regular local-profile tabs enter the durable projection.

`BrowserSessionPersistenceRules` projects only regular tabs from local profiles into a versioned,
bounded snapshot. Selection falls back within the active local profile; private-only sessions restore a
blank regular tab. Engine history/loading flags and previews never enter this envelope. Native atomic
storage restores local profiles first, then applies the shared session policy before attaching engine
sessions. Private-tab creation, pin ordering and pinned close protection belong to the same shared
controller. The Apple plus-button context menu calls that private-tab policy directly.

`BrowserLibraryController` owns validated favorite mutations and profile-scoped history. Its limits
(100 favorites and 250 history visits) are shared with Android's existing library. Common URL identity
ignores fragments and default ports, while retaining path/query case. Private tabs cannot record visits
or create persistent favorites. Native hosts provide only bounded record encoding and atomic I/O.
Android uses the same generic
history-retention, favorite-toggle and normalized-query mechanics with its existing rich records and
canonical URL rules.

`BrowserSitePreferenceController` owns per-profile exact-host desktop preferences for WebKit;
private overrides stay inside the initiating live tab and never enter the durable projection. The
shared URL parser supplies host identity for every navigation. Android retains its existing public-suffix
registrable-domain policy while both platforms call the same bounded enable/disable retention rules.

`BrowserLibraryScreen` renders searchable Favorites and History, row deletion, favorite undo and a
confirmation before clearing the active profile's history. It receives immutable snapshots/actions;
no SwiftUI library renderer is required.

Snooze also uses production Android code in common Kotlin: `SnoozedTab`, mutation/undo/due-restore
rules, `TabPinningRules`, the preset dialog and management card/list. `SnoozeClockRules` owns timezone
and DST calendar decisions; Android wraps its existing Java picker types, while iOS supplies a native
custom date picker. The shared session controller removes regular tabs into the same versioned atomic
active+snoozed envelope and restores due entries without attaching hidden engines. Private, remote synced
and protected sources cannot create Snoozes. Shared notification eligibility excludes private/protected
content; native OS authorization, reminder limits and foreground wake timers remain platform edges.

Shared profile management also owns close-all-unpinned, move-to-profile and safe deletion into a local
fallback. Deletion reassigns both open and snoozed ownership. Apple handles website-data-store cleanup
before committing deletion, retaining metadata when that cleanup fails. Profile reorder remains open.

`DownloadFileNameRules` is the existing Android filename policy moved into common Kotlin. Android keeps
its platform header/URL parsing; WebKit supplies its suggested filename to the same sanitizer. Native
WebKit transfer progress, protected temporary files and Files export stay at the Apple edge. There is no
persistent iOS download history/queue, background transfer or resume adapter yet. Basic camera/microphone/
foreground-location permission prompts and file selection remain WebKit-owned, with native purpose strings;
Candy does not duplicate the system permission store or auto-grant access.

`BrowserFeatureMenuRules` projects Candy's complete Toolbar, Page, Candy and Browser sections into
stable actions, label keys, ordering, kinds and enabled/toggle states. Android feeds that model to the
common `BrowserMainMenu`; iOS uses `BrowserMenuBottomSheetContent` by default and offers its native
floating presentation from the identical ordered model. Both route commands through the same action sink.
`BrowserMenuPresentationSettings` shares style, content layout and toolbar-slot editing. Toolbar
assignment reveals a hidden action atomically while retaining its existing switcher membership.
Firefox extensions are the one
Android-only additive capability.
`BrowserCoreMenuRules` remains the temporary minimal bootstrap projection while every action migrates to
the complete contract. `BrowserTabSwitchGestureRules`
matches Android tab switching:
24% viewport travel, or a direction-matching 900-point/second fling after 24 points. Editing the
address and vertical-dominant gestures suppress switching.

`TabOverviewHeroPager`, `CompactTabGrid`, `CompactTabList`, `ProfileSwitcher` and
`TabOverviewBottomChrome` are the existing
Android production sources moved into `commonMain` and called by Android and iOS. Entry/exit hero motion
and the production `TabActionsMenu` call site are shared as well. The overview background extends below
the iOS home indicator while its controls consume the navigation inset. Profile reorder and
favicon/incognito artwork adapters remain open. Find remains an Apple system presentation.

`ReaderStudioScreen`, `CandyTrailScreenContent`, their models and deterministic rules also live in
`commonMain`. iOS maps bounded WebKit extraction and history snapshots into those contracts; it does not
own parallel Reader or Trail UI. Reader library settings, progress and offline documents use the shared
`ReaderLibraryController` and
`ReaderLibraryCodec`; Android's AtomicFile store and Apple's protected atomic text store use the same
version-one format, extraction validation, UUID/provenance rules and 8-MiB eviction budget. Private Reader
state remains empty and cannot mutate that store. iOS supplies native speech behind `ReaderSpeech` while
the common reducer owns play/pause/range state. Failed extraction can resume a matching saved article,
and the existing shared offline-library renderer remains accessible. Trail state remains session-memory-only on iOS.

Settings uses the same migration boundary: `SettingsDestination`, `SettingsRouter`, common controls,
home ordering, full Appearance page, Tabs overview/dismiss controls and Browser translation-provider
control live in `commonMain`. The shared Toppings destination renders list, add/edit, enable/disable and
delete flows. Its viewport snapshot carries only ID/name/enabled metadata; source is fetched lazily when
the editor opens and every mutation crosses the narrow platform action sink. Android supplies
string/drawable/frosted-surface and persisted-state adapters. iOS `OpenSettings` reaches the same pages.
The shared Appearance form uses additive capabilities: Android retains every existing control, while iOS
shows persisted System/Light/Dark, animation and native page-size choices. Apple uses the same normalized
`AppearanceSettings` values, applies native page zoom at the engine edge and combines the user motion
setting with system Reduce Motion. Unsupported backend settings remain omitted or disabled rather than
being rebuilt in SwiftUI.

Candy Sync uses the same shared repository and Settings page on Android and iOS. Native code supplies only
transport, secure storage, cryptography, recovery-key derivation and scheduling. Shared profile reconciliation
links the current device into one existing local profile and projects other devices into isolated synced profiles;
WebKit maps those isolated profiles to stable named website-data stores.

`BrowserChromeScrollRules`, `AddressBarDockPlacement`, `AddressBarDockingRules` and gesture constants
also live in `commonMain`. Android keeps its rate dispatcher and Compose gesture execution; iOS
uses scalar point adapters for the same scroll thresholds, resistance, snap and offset geometry.
The native host owns session acceptance, observed offsets and global chrome preference storage.

`AddressLoadCapsuleFeedback` also lives in `commonMain`. Its pure rules resolve load/settle state and a
closed, phase-shifted Candy rainbow. `AddressBarMorphRules` owns bounded resistance, scale and corner math,
so platform chrome supplies measured geometry without duplicating the visual algorithm.

```text
BrowserSessionController (common Kotlin)
  |-- BrowserEngineCommand --> BrowserEngineSessionPort (platform adapter)
  |<-- BrowserEngineEvent  --- WKWebView / GeckoSession
  `-- BrowserTabsController --> observable tab state
```

Build the shared simulator framework from the repository root:

```sh
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

Run common tests on the simulator target:

```sh
./gradlew :shared:iosSimulatorArm64Test
```

Agent sessions with a dedicated CoreSimulator device set use the isolated gate so Gradle never
falls back to another session's simulator:

```sh
CANDY_IOS_SIMULATOR_DEVICE_SET=/tmp/candy-browser-simulator-devices \
CANDY_IOS_SIMULATOR_UDID=<dedicated-udid> \
./gradlew :shared:iosSimulatorArm64IsolatedTest
```

Private-tab creation keeps an eligible local profile or selects a local fallback when invoked from a remote synced profile. Remote profile reconciliation cannot own or remove that private tab; private records remain excluded from persistence and sync.
