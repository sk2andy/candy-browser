#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 || $# -gt 5 ]]; then
    echo "Usage: $0 <emulator-serial> <baseline|current|candidate|force-scroll|safe-area> [start-rank] [site-count] [target-ranks]" >&2
    exit 2
fi

serial="$1"
audit_pass="$2"
start_rank="${3:-1}"
site_count="${4:-25}"
target_ranks="${5:-}"
app_id="dev.sk2andy.materialbrowser.candyaudit"
gradle_args=(
    -Pcandy.debugApplicationIdSuffix=.candyaudit
    '-Pcandy.debugAppLabel=Candy Site Audit'
)

if [[ "$audit_pass" == "safe-area" ]]; then
    app_id="dev.sk2andy.materialbrowser.edgeaudit"
    gradle_args+=(
        -Pcandy.debugApplicationIdSuffix=.edgeaudit
        '-Pcandy.debugAppLabel=Candy Edge Audit'
    )
fi
test_app_id="${app_id}.test"

case "$audit_pass" in
    baseline|current|candidate|force-scroll|safe-area) ;;
    *)
        echo "Invalid pass: $audit_pass" >&2
        exit 2
        ;;
esac

if [[ "$(adb -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r')" != "1" ]]; then
    echo "Refusing to audit on non-emulator device: $serial" >&2
    exit 1
fi

adb -s "$serial" shell input keyevent KEYCODE_WAKEUP
adb -s "$serial" shell wm dismiss-keyguard
adb -s "$serial" shell settings put global stay_on_while_plugged_in 3

if [[ "${CANDY_AUDIT_SKIP_BUILD:-false}" != "true" ]]; then
    ./gradlew assembleDebug assembleDebugAndroidTest "${gradle_args[@]}"
fi
app_apk="app/build/outputs/apk/debug/app-debug.apk"
test_apk="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
build_id="$(shasum -a 256 "$app_apk" | awk '{print $1}')"
adb -s "$serial" install -r -d "$app_apk"
adb -s "$serial" shell pm clear "$app_id" >/dev/null
adb -s "$serial" install -r -d "$test_apk"
instrument_args=(
    -e candyAudit true
    -e auditPass "$audit_pass"
    -e buildId "$build_id"
    -e startRank "$start_rank"
    -e siteCount "$site_count"
)
if [[ -n "$target_ranks" ]]; then
    instrument_args+=(-e targetRanks "$target_ranks")
    start_rank="${target_ranks%%,*}"
    end_rank="${target_ranks##*,}"
else
    end_rank=$((start_rank + site_count - 1))
fi

set +e
adb -s "$serial" shell am instrument -w -r \
    "${instrument_args[@]}" \
    -e class dev.sk2andy.materialbrowser.blocking.TopSiteBlockingAuditInstrumentedTest \
    "$test_app_id/androidx.test.runner.AndroidJUnitRunner"
instrument_status=$?
set -e

remote_root="/sdcard/Android/data/${app_id}/files/top-site-audit"
local_root="build/top-site-audit/$audit_pass"
mkdir -p "$local_root"
adb -s "$serial" pull \
    "$remote_root/sites-$audit_pass-$start_rank-$end_rank.jsonl" \
    "$local_root/"
if adb -s "$serial" shell test -d "$remote_root/screenshots/$audit_pass"; then
    adb -s "$serial" pull "$remote_root/screenshots/$audit_pass" "$local_root/screenshots/"
fi
if [[ -n "$target_ranks" ]]; then
    expected_ranks="$target_ranks"
else
    expected_ranks="$(awk -v first_rank="$start_rank" -v last_rank="$end_rank" 'BEGIN { for (rank=first_rank; rank<=last_rank; rank++) printf "%s%d", (rank == first_rank ? "" : ","), rank }')"
fi
output="$local_root/sites-$audit_pass-$start_rank-$end_rank.jsonl"
if ! node scripts/validate_site_audit_batch.mjs \
    "$output" "$audit_pass" "$build_id" "$expected_ranks"; then
    lines="$(wc -l < "$output" | tr -d ' ')"
    partial_root="$local_root/partial"
    mkdir -p "$partial_root"
    partial_name="sites-$audit_pass-$start_rank-$end_rank-$lines-records-$(date -u +%Y%m%dT%H%M%SZ)-$$.jsonl"
    mv "$output" "$partial_root/$partial_name"
    echo "Archived invalid batch ($lines records): $partial_root/$partial_name" >&2
    exit 1
fi

echo "Results: $local_root"
exit "$instrument_status"
