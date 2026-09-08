# Candy shared scaffold

This Gradle module is Candy's Kotlin Multiplatform boundary. The Android application consumes the
same module that is exported to iOS. It targets Android, iOS devices, and Apple-silicon simulators.

| Source | Responsibility |
| --- | --- |
| `commonMain/browser` | HTTP(S) and page-translation URL rules, tab/session reducers, feature-menu presentation, chrome gestures and exact tab-overview geometry |
| `commonMain/topping` | Topping metadata parsing and platform-neutral injection plans |
| `commonMain/ui` | Compose Multiplatform root plus production menu, tab overview, address-load/morph and settings core renderers |
| `commonMain/sync` | Candy Sync repository, protocol codecs, encrypted-outbox rules and native adapter contracts |
| `iosMain/ui` | `ComposeUIViewController` plus the native `WKWebView` viewport seam |
| `iosApp` | Thin SwiftUI system presenter, `WKWebView`, blocking and `WKUserScript` adapters |

`commonMain` deliberately contains no Android, WebKit, SwiftUI, storage, or native image types.
Compose Multiplatform 1.8.2 remains on the existing KMP Android plugin while GeckoView 155 requires
compileSdk 37.1 / AGP 9.4. Built-in Kotlin stays disabled as a temporary AGP 9 migration bridge.
`BrowserViewportSnapshot` and `BrowserViewportActionSink` are the strangler seam: Kotlin owns the
browser chrome state/actions and physically shared production overview, Reader, Trails and Settings.
Android renders the common chrome/menu composables; iOS projects only that same chrome/menu state into
native Liquid Glass surfaces while each platform embeds its engine view. The iOS app
converts a `ToppingInjectionPlan` into a main-frame-only `WKUserScript` in a named content world.
It does not host WebExtensions.

`BrowserSessionController` owns the `tab ID -> BrowserEngineSessionPort` registry and sends typed
`BrowserEngineCommand` values. Platform adapters own engine instances, execute those commands and
return `BrowserEngineEvent` values; shared Kotlin never imports `WKWebView`, `WebView` or GeckoView.
The nested `BrowserTabsController` remains the source of truth for tab identity, selection,
navigation state, overview visibility and address-focus requests. New tabs are memory-only, blank,
selected immediately and request address focus.

`BrowserFeatureMenuRules` projects Candy's complete Toolbar, Page, Candy and Browser sections into
stable actions, label keys, ordering, kinds and enabled/toggle states. Android feeds that model to the
common `BrowserMainMenu`; iOS renders a native SF Symbols/Liquid Glass presentation from the identical
ordered model and routes every command back through the same action sink. Firefox extensions are the one
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
the iOS home indicator while its controls consume the navigation inset. Current iOS gaps are persisted
profile editing/reorder orchestration, favicon and incognito artwork adapters, feature implementations behind history/snooze menu
entries, and the native Find sheet.

`ReaderStudioScreen`, `CandyTrailScreenContent`, their models and deterministic rules also live in
`commonMain`. iOS maps bounded WebKit extraction and history snapshots into those contracts; it does not
own parallel Reader or Trail UI. iOS Reader storage and Trail state are currently session-memory-only, and
iOS speech is exposed as unavailable until a native speech adapter is connected.

Settings uses the same migration boundary: `SettingsDestination`, `SettingsRouter`, common controls,
home ordering, full Appearance page, Tabs overview/dismiss controls and Browser translation-provider
control live in `commonMain`. The shared Toppings destination renders list, add/edit, enable/disable and
delete flows. Its viewport snapshot carries only ID/name/enabled metadata; source is fetched lazily when
the editor opens and every mutation crosses the narrow platform action sink. Android supplies
string/drawable/frosted-surface and persisted-state adapters. iOS `OpenSettings` reaches the same pages;
unsupported backend settings stay disabled rather than being rebuilt in SwiftUI.

Candy Sync uses the same shared repository and Settings page on Android and iOS. Native code supplies only
transport, secure storage, cryptography, recovery-key derivation and scheduling. Shared profile reconciliation
links the current device into one existing local profile and projects other devices into isolated synced profiles;
WebKit maps those isolated profiles to stable named website-data stores.

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
