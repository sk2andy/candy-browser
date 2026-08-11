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
    *)
        echo "Invalid full-audit pass: $audit_pass" >&2
        exit 2
        ;;
esac
if ! [[ "$batch_size" =~ ^[0-9]+$ ]] || ((batch_size < 1 || batch_size > 100)); then
    echo "Batch size must be between 1 and 100" >&2
    exit 2
fi

./gradlew assembleDebug assembleDebugAndroidTest \
    -Pcandy.debugApplicationIdSuffix=.candyaudit \
    '-Pcandy.debugAppLabel=Candy Site Audit'
build_id="$(shasum -a 256 app/build/outputs/apk/debug/app-debug.apk | awk '{print $1}')"

for ((start_rank = 1; start_rank <= 10000; start_rank += batch_size)); do
    remaining=$((10001 - start_rank))
    count="$batch_size"
    if ((remaining < batch_size)); then count="$remaining"; fi
    end_rank=$((start_rank + count - 1))
    output="build/top-site-audit/$audit_pass/sites-$audit_pass-$start_rank-$end_rank.jsonl"
    expected_ranks="$(awk -v first_rank="$start_rank" -v last_rank="$end_rank" 'BEGIN { for (rank=first_rank; rank<=last_rank; rank++) printf "%s%d", (rank == first_rank ? "" : ","), rank }')"
    if [[ -f "$output" ]]; then
        if node scripts/validate_site_audit_batch.mjs \
            "$output" "$audit_pass" "$build_id" "$expected_ranks"; then
            echo "Skip completed ranks $start_rank-$end_rank"
            continue
        fi
        lines="$(wc -l < "$output" | tr -d ' ')"
        partial_root="build/top-site-audit/$audit_pass/partial"
        mkdir -p "$partial_root"
        partial_name="sites-$audit_pass-$start_rank-$end_rank-$lines-records-$(date -u +%Y%m%dT%H%M%SZ).jsonl"
        mv "$output" "$partial_root/$partial_name"
        echo "Archived incomplete batch ($lines/$count records): $partial_root/$partial_name"
    fi
    CANDY_AUDIT_SKIP_BUILD=true scripts/run_top_site_audit.sh \
        "$serial" "$audit_pass" "$start_rank" "$count"
done
