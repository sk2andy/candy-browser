# Candy shared scaffold

This Gradle module is Candy's Kotlin Multiplatform boundary. The Android application consumes the
same module that is exported to iOS. It targets Android, iOS devices, and Apple-silicon simulators.

| Source | Responsibility |
| --- | --- |
| `commonMain/browser` | HTTP(S) resolution, tab/session reducers, feature-menu presentation, chrome gestures and exact tab-overview geometry |
| `commonMain/topping` | Topping metadata parsing and platform-neutral injection plans |
| `commonMain/ui` | Compose Multiplatform root, Candy chrome, menu and Hero/Grid/List overview |
| `iosMain/ui` | `ComposeUIViewController` plus the native `WKWebView` viewport seam |
| `iosApp` | Thin SwiftUI system presenter, `WKWebView`, blocking and `WKUserScript` adapters |

`commonMain` deliberately contains no Android, WebKit, SwiftUI, storage, or native image types.
Compose Multiplatform 1.8.2 keeps the existing compileSdk 35 / AGP 8.7 Android toolchain viable.
`BrowserViewportSnapshot` and `BrowserViewportActionSink` are the strangler seam: Kotlin renders
the shared browser chrome and overview while each platform embeds its engine view. The iOS app
converts a `ToppingInjectionPlan` into a main-frame-only `WKUserScript` in a named content world.
It does not host WebExtensions.

`BrowserSessionController` owns the `tab ID -> BrowserEngineSessionPort` registry and sends typed
`BrowserEngineCommand` values. Platform adapters own engine instances, execute those commands and
return `BrowserEngineEvent` values; shared Kotlin never imports `WKWebView`, `WebView` or GeckoView.
The nested `BrowserTabsController` remains the source of truth for tab identity, selection,
navigation state, overview visibility and address-focus requests. New tabs are memory-only, blank,
selected immediately and request address focus.

`BrowserFeatureMenuRules` projects Candy's complete Toolbar, Page, Candy and Browser sections into
stable actions, label keys, ordering, kinds and enabled/toggle states. Platform renderers translate
label keys and icons; they do not own a separate menu tree. Firefox extensions are the one
Android-only additive capability. `BrowserCoreMenuRules` remains the temporary minimal bootstrap
projection while both renderers migrate to the complete contract. `BrowserTabSwitchGestureRules`
matches Android tab switching:
24% viewport travel, or a direction-matching 900-point/second fling after 24 points. Editing the
address and vertical-dominant gestures suppress switching.

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
