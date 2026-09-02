#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$sdk_root" ]]; then
  if [[ -d "$HOME/Library/Android/sdk" ]]; then
    sdk_root="$HOME/Library/Android/sdk"
  else
    sdk_root="$HOME/Android/Sdk"
  fi
fi

sdkmanager="$sdk_root/cmdline-tools/latest/bin/sdkmanager"
avdmanager="$sdk_root/cmdline-tools/latest/bin/avdmanager"
emulator="$sdk_root/emulator/emulator"
adb="$sdk_root/platform-tools/adb"
for tool in "$sdkmanager" "$avdmanager" "$emulator" "$adb"; do
  if [[ ! -x "$tool" ]]; then
    echo "Missing Android SDK tool: $tool" >&2
    exit 1
  fi
done

api="${CANDY_SYNC_TEST_API:-35}"
case "$(uname -m)" in
  arm64|aarch64) abi="arm64-v8a" ;;
  *) abi="x86_64" ;;
esac
image="system-images;android-${api};google_apis_playstore;${abi}"
if [[ ! -d "$sdk_root/system-images/android-${api}/google_apis_playstore/${abi}" ]]; then
  yes | "$sdkmanager" "$image" >/dev/null ||
    [[ -d "$sdk_root/system-images/android-${api}/google_apis_playstore/${abi}" ]]
fi

avd_name="candy_sync_test_${$}"
printf 'no\n' | "$avdmanager" create avd \
  --force \
  --name "$avd_name" \
  --package "$image" \
  --device pixel_7 >/dev/null

serial=""
port=""
emulator_pid=""
cleanup() {
  if [[ -n "$serial" ]]; then
    "$adb" -s "$serial" emu kill >/dev/null 2>&1 || true
  elif [[ -n "$emulator_pid" ]]; then
    kill "$emulator_pid" >/dev/null 2>&1 || true
  fi
  "$avdmanager" delete avd --name "$avd_name" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

for candidate in $(seq 5554 2 5584); do
  if ! "$adb" devices | awk 'NR > 1 { print $1 }' | grep -qx "emulator-${candidate}"; then
    serial="emulator-${candidate}"
    port="$candidate"
    break
  fi
done
if [[ -z "$serial" ]]; then
  echo "No free Android emulator port in 5554..5584" >&2
  exit 1
fi

log_file="$(mktemp -t candy-sync-emulator.XXXXXX.log)"
"$emulator" \
  -avd "$avd_name" \
  -port "$port" \
  -no-window \
  -no-audio \
  -no-boot-anim \
  -no-snapshot \
  -wipe-data >"$log_file" 2>&1 &
emulator_pid="$!"

for _ in $(seq 1 180); do
  if [[ "$("$adb" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]]; then
    break
  fi
  if ! kill -0 "$emulator_pid" 2>/dev/null; then
    cat "$log_file" >&2
    exit 1
  fi
  sleep 1
done
if [[ "$("$adb" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != 1 ]]; then
  cat "$log_file" >&2
  echo "Android emulator did not finish booting" >&2
  exit 1
fi

"$adb" -s "$serial" shell settings put global window_animation_scale 0
"$adb" -s "$serial" shell settings put global transition_animation_scale 0
"$adb" -s "$serial" shell settings put global animator_duration_scale 0
"$adb" -s "$serial" shell input keyevent 82

cd "$repo_root"
./gradlew testFullDebugUnitTest testFossDebugUnitTest
ANDROID_SERIAL="$serial" ./gradlew connectedFullDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.sk2andy.materialbrowser.data.sync.AndroidSyncSecurityInstrumentedTest,dev.sk2andy.materialbrowser.ui.ProfileCreationSheetInstrumentedTest,dev.sk2andy.materialbrowser.ui.ProfileSwitcherInstrumentedTest,dev.sk2andy.materialbrowser.ui.SyncSettingsPageInstrumentedTest
