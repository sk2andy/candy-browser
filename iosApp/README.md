# Candy iOS scaffold

This target is a thin Apple host around a shared Compose Multiplatform browser root.
Browser-neutral URL/navigation, engine commands/events, tab intents, feature menu,
chrome gestures, visible chrome and Hero/Grid/List overview live in `../shared`.
Apple owns lifecycle, one `WKWebView` per tab and mandatory system presenters only.
`CandyComposeHost` mounts the shared `ComposeUIViewController` and overlays Find, Reader,
Translate, Share and error presentation; it does not render a second browser bar or overview.
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
the pending result.

The legacy native overview can render accepted images with aspect-fill inside the Candy hero
card. The active shared Compose overview reads the same `BrowserTabOverviewLayoutRules.heroCard` Kotlin model as
Android: portrait width is 74% clamped to 244...360 points with a 0.45 aspect
ratio; landscape width is 68% clamped to
360...720 points and additionally capped by 66% viewport height at a 1.6 aspect
ratio. Card radius is 28 points and page spacing is 12 points. Grid geometry comes
from the same shared `grid` rule: two columns on phones, three in
landscape from 900 points, 16-point padding, 12-point spacing, and portrait/landscape
preview ratios of 0.72/1.6. List rows mirror Android's 64-point height, 16-point
horizontal padding, 8-point spacing and 18-point radius through the shared `list`
rule. All modes render the same
title/address fallback and selected outline. Native `UIImage` snapshots are deliberately not
passed through the current platform-neutral Compose seam yet. This is an explicit migration gap,
not claimed preview parity. Snapshot placement rules remain shared for the next adapter slice.

Hero, grid and list can be changed through the visible switcher in the overview
header. Only that presentation preference is stored in `UserDefaults`; snapshots
remain memory-only. Missing or unknown stored values safely fall back to Hero.

## Remaining visual differences from Android

| Area | iOS difference |
| --- | --- |
| Transition | Shared iOS Compose currently uses basic lazy-list transitions; Android morphs the live viewport into the selected card and back |
| Background | Shared iOS Compose currently uses the common neutral surface; Liquid Glass is deferred until sharing is complete |
| Previews | Shared overview currently shows title/address fallbacks; native memory-only `UIImage` snapshots are not connected to Compose |
| Chrome | Shared anatomy/actions/icons/gestures are in place; exact Android typography, motion and profile artwork remain to be extracted |
| Icon and fallback | Shared Compose uses Material vectors and text fallback; favicon/domain artwork remains to be moved into the common model |
| Selection | Shared Compose draws a three-point accent outline in every mode; Android uses additional mode-specific motion/treatment |
| List preview | Shared Compose has no native image handle yet; Android's row can show a favicon |
| Mode control | Shared Compose exposes three buttons in the overview header; Android also exposes the preference in tab settings |
| Extended behavior | Android additionally has drag-dismiss, drag-reorder, pinned-tab jump, private/profile treatments and haptics |

These are renderer and interaction differences. Tab IDs, engine sessions, selection,
close behavior, Hero/Grid geometry and stale-result rules keep the same
core ownership boundaries.

The shared Compose bottom Candy chrome is `tab count | address | plus | vertical more`. Back,
forward, reload/stop, favorite/pin, new tab, close tab and tabs live in the more menu
projected and rendered from shared `BrowserFeatureMenuRules`. Both the count button and a dominant 56-point upward drag open the same
overview. A horizontal address-bar swipe selects the adjacent tab after 24% viewport
travel, or after 24 points at 900 points/second. Address editing suppresses both gestures.

| iOS feature action | Native implementation |
| --- | --- |
| Favorite / pin | In-memory URL favorite and per-tab pin state; dynamic label plus badges in every overview mode; pin disables/hides close until unpinned |
| Find in page | Liquid-Glass search bar backed by `WKWebView.find` with previous/next/wrap; callbacks are bound to source tab and WebKit session |
| Reader | JavaScript-bounded `article`/`main`/body text extraction into a native readable sheet, accepted only for the initiating tab/session/URL/request |
| Translate | The same bounded and stale-safe extraction opens Apple's system translation presentation; its privacy consent remains system-owned |
| Share / external / print | `UIActivityViewController`, `UIApplication.open`, and `UIPrintInteractionController` with the web view formatter |

Actions without an iOS implementation are not rendered. Current explicit gaps are
address-bar docking, site toggles, dynamic Topping commands, Candy Trail, Site Capsule,
summarization, snoozing, snoozed tabs, history and settings. Firefox Extensions remains Android-only. Favorite
and pin state are currently session-memory only; persistence and pinned-tab ordering are
still future storage work.

`ToppingInstaller` installs validated JavaScript as a top-frame-only `WKUserScript`
in a named `WKContentWorld`. This is not a Safari Web Extension host and does not
promise WebExtension API compatibility.

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
```
