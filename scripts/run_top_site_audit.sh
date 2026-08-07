#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 || $# -gt 4 ]]; then
    echo "Usage: $0 <emulator-serial> <baseline|current|candidate> [start-rank] [site-count]" >&2
    exit 2
fi

serial="$1"
audit_pass="$2"
start_rank="${3:-1}"
site_count="${4:-25}"
app_id="dev.sk2andy.materialbrowser.debug"
test_app_id="${app_id}.test"

case "$audit_pass" in
    baseline|current|candidate) ;;
    *)
        echo "Invalid pass: $audit_pass" >&2
        exit 2
        ;;
esac

if [[ "$(adb -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r')" != "1" ]]; then
    echo "Refusing to audit on non-emulator device: $serial" >&2
    exit 1
fi

./gradlew assembleDebug assembleDebugAndroidTest
app_apk="app/build/outputs/apk/debug/app-debug.apk"
test_apk="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
build_id="$(shasum -a 256 "$app_apk" | awk '{print $1}')"
adb -s "$serial" install -r -d "$app_apk"
adb -s "$serial" shell pm clear "$app_id" >/dev/null
adb -s "$serial" install -r -d "$test_apk"
adb -s "$serial" shell am instrument -w -r \
    -e candyAudit true \
    -e auditPass "$audit_pass" \
    -e buildId "$build_id" \
    -e startRank "$start_rank" \
    -e siteCount "$site_count" \
    -e class dev.sk2andy.materialbrowser.blocking.TopSiteBlockingAuditInstrumentedTest \
    "$test_app_id/androidx.test.runner.AndroidJUnitRunner"

end_rank=$((start_rank + site_count - 1))
remote_root="/sdcard/Android/data/${app_id}/files/top-site-audit"
local_root="build/top-site-audit/$audit_pass"
mkdir -p "$local_root"
adb -s "$serial" pull \
    "$remote_root/sites-$audit_pass-$start_rank-$end_rank.jsonl" \
    "$local_root/"
if adb -s "$serial" shell test -d "$remote_root/screenshots/$audit_pass"; then
    adb -s "$serial" pull "$remote_root/screenshots/$audit_pass" "$local_root/screenshots/"
fi

echo "Results: $local_root"
