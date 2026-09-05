# Candy iOS scaffold

This target is deliberately native SwiftUI around `WKWebView`. Browser-neutral URL,
navigation and Topping rules live in `../shared`; Apple lifecycle, WebKit and Liquid
Glass stay here.

`ToppingInstaller` installs validated JavaScript as a top-frame-only `WKUserScript`
in a named `WKContentWorld`. This is not a Safari Web Extension host and does not
promise WebExtension API compatibility.

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
DEVICE_ID=ABBBC76D-677A-4D64-882C-6AE28BD7CDAB
APP="$PWD/iosApp/xcode-build/Debug-iphonesimulator/CandyIos.app"

xcrun simctl --set "$DEVICE_SET" boot "$DEVICE_ID"
xcrun simctl --set "$DEVICE_SET" bootstatus "$DEVICE_ID" -b
codesign --force --sign - "$APP"
xcrun simctl --set "$DEVICE_SET" install "$DEVICE_ID" "$APP"
xcrun simctl --set "$DEVICE_SET" launch "$DEVICE_ID" \
  dev.sk2andy.candy.ios.scaffold
```
