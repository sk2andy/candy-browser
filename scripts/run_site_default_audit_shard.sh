#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 3 || $# -gt 4 ]]; then
    echo "Usage: $0 <emulator-serial> <start-rank> <end-rank> [batch-size]" >&2
    exit 2
fi

serial="$1"
first_rank="$2"
last_rank="$3"
batch_size="${4:-25}"
if ! [[ "$first_rank" =~ ^[0-9]+$ && "$last_rank" =~ ^[0-9]+$ && "$batch_size" =~ ^[0-9]+$ ]] ||
    ((first_rank < 1 || last_rank > 10000 || first_rank > last_rank || batch_size < 1)); then
    echo "Invalid rank range or batch size" >&2
    exit 2
fi

app_apk="app/build/outputs/apk/full/debug/app-full-debug.apk"
test_apk="app/build/outputs/apk/androidTest/full/debug/app-full-debug-androidTest.apk"
if [[ ! -f "$app_apk" || ! -f "$test_apk" ]]; then
    echo "Candy audit APKs are missing; build them before starting shards" >&2
    exit 1
fi
build_id="$(shasum -a 256 "$app_apk" | awk '{print $1}')"

for audit_pass in baseline current; do
    for ((start_rank = first_rank; start_rank <= last_rank; start_rank += batch_size)); do
        remaining=$((last_rank - start_rank + 1))
        count="$batch_size"
        if ((remaining < batch_size)); then count="$remaining"; fi
        end_rank=$((start_rank + count - 1))
        output="build/top-site-audit/$audit_pass/sites-$audit_pass-$start_rank-$end_rank.jsonl"
        expected_ranks="$(awk -v first="$start_rank" -v last="$end_rank" 'BEGIN { for (rank=first; rank<=last; rank++) printf "%s%d", (rank == first ? "" : ","), rank }')"
        if [[ -f "$output" ]]; then
            if node scripts/validate_site_audit_batch.mjs \
                "$output" "$audit_pass" "$build_id" "$expected_ranks"; then
                echo "[$serial] Skip $audit_pass ranks $start_rank-$end_rank"
                continue
            fi
            partial_root="build/top-site-audit/$audit_pass/partial"
            mkdir -p "$partial_root"
            record_count="$(wc -l < "$output" | tr -d ' ')"
            partial_name="sites-$audit_pass-$start_rank-$end_rank-$record_count-records-$(date -u +%Y%m%dT%H%M%SZ).jsonl"
            mv "$output" "$partial_root/$partial_name"
            echo "[$serial] Archived incomplete batch: $partial_root/$partial_name"
        fi
        CANDY_AUDIT_SKIP_BUILD=true scripts/run_top_site_audit.sh \
            "$serial" "$audit_pass" "$start_rank" "$count"
    done
done
