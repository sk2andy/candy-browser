#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 || $# -gt 3 ]]; then
    echo "Usage: $0 <emulator-serial> <baseline|current> [batch-size]" >&2
    exit 2
fi

serial="$1"
audit_pass="$2"
batch_size="${3:-25}"
case "$audit_pass" in
    baseline|current) ;;
    *) echo "Invalid audit pass: $audit_pass" >&2; exit 2 ;;
esac
if ! [[ "$batch_size" =~ ^[0-9]+$ ]] || ((batch_size < 1 || batch_size > 100)); then
    echo "Batch size must be between 1 and 100" >&2
    exit 2
fi

app_apk="app/build/outputs/apk/debug/app-debug.apk"
test_apk="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
if [[ ! -f "$app_apk" || ! -f "$test_apk" ]]; then
    echo "Candy audit APKs are missing; build them before filling gaps" >&2
    exit 1
fi

missing_file="$(mktemp "${TMPDIR:-/tmp}/candy-missing-site-audit.XXXXXX")"
trap 'rm -f "$missing_file"' EXIT
if ! node scripts/list_missing_site_audit_ranks.mjs \
    "$audit_pass" "$app_apk" "$batch_size" > "$missing_file"; then
    echo "Failed to determine missing audit ranks" >&2
    exit 1
fi

while IFS= read -r target_ranks; do
    [[ -n "$target_ranks" ]] || continue
    start_rank="${target_ranks%%,*}"
    count="$(awk -F, '{print NF}' <<< "$target_ranks")"
    CANDY_AUDIT_SKIP_BUILD=true scripts/run_top_site_audit.sh \
        "$serial" "$audit_pass" "$start_rank" "$count" "$target_ranks"
done < "$missing_file"

if ! node scripts/list_missing_site_audit_ranks.mjs \
    "$audit_pass" "$app_apk" "$batch_size" > "$missing_file"; then
    echo "Failed final audit coverage validation" >&2
    exit 1
fi
if [[ -s "$missing_file" ]]; then
    echo "Audit still has missing ranks after gap fill" >&2
    exit 1
fi
