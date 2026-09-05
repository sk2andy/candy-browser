# Candy shared scaffold

This Gradle module is Candy's Kotlin Multiplatform boundary. The Android application consumes the
same module that is exported to iOS. It targets Android, iOS devices, and Apple-silicon simulators.

| Source | Responsibility |
| --- | --- |
| `commonMain/browser` | Bounded HTTP(S) resolution plus navigation/chrome state and actions |
| `commonMain/topping` | Topping metadata parsing and platform-neutral injection plans |
| `iosApp` | SwiftUI, `WKWebView`, Liquid Glass and `WKUserScript` installation |

`commonMain` deliberately contains no Android, WebKit, Compose, SwiftUI, storage, or native image
types. The iOS app converts a `ToppingInjectionPlan` into a main-frame-only `WKUserScript` in a named
content world. It does not host WebExtensions.

Build the shared simulator framework from the repository root:

```sh
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

Run common tests on the simulator target:

```sh
./gradlew :shared:iosSimulatorArm64Test
```
