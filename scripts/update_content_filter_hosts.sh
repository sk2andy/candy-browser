#!/bin/sh
set -eu

# EasyList/EasyPrivacy are also used by Privacy Browser Android. Only plain host-anchored
# block rules and host-wide, domain-scoped allow exceptions are compiled here. Resource-type
# exceptions are deliberately broadened only for named pages because System WebView does not
# expose request type to shouldInterceptRequest; path-specific exceptions remain unsupported.
# https://www.stoutner.com/privacy-browser-android/filter-lists/
# https://github.com/easylist/easylist
SOURCE_REVISION="54849f55642f155a67649b46fe3b87c39607c1c5"
SOURCE_ROOT="https://raw.githubusercontent.com/easylist/easylist/$SOURCE_REVISION"
SOURCE_PATHS="
easylist/easylist_adservers.txt
easylist/easylist_thirdparty.txt
easyprivacy/easyprivacy_trackingservers.txt
easyprivacy/easyprivacy_thirdparty.txt
"
ALLOW_SOURCE_PATHS="
easylist/easylist_allowlist.txt
easyprivacy/easyprivacy_allowlist.txt
easyprivacy/easyprivacy_allowlist_international.txt
"
PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUTPUT_FILE="$PROJECT_DIR/app/src/main/assets/easylist_blocked_hosts.txt"
ALLOW_OUTPUT_FILE="$PROJECT_DIR/app/src/main/assets/easylist_allowed_host_pairs.txt"
TEMP_DIR=$(mktemp -d)
trap 'rm -rf "$TEMP_DIR"' EXIT HUP INT TERM

: > "$TEMP_DIR/source.txt"
for source_path in $SOURCE_PATHS; do
    curl --fail --location --silent --show-error \
        "$SOURCE_ROOT/$source_path" >> "$TEMP_DIR/source.txt"
done
: > "$TEMP_DIR/allow_source.txt"
for source_path in $ALLOW_SOURCE_PATHS; do
    curl --fail --location --silent --show-error \
        "$SOURCE_ROOT/$source_path" >> "$TEMP_DIR/allow_source.txt"
done

awk '
    /^\|\|[A-Za-z0-9.-]+\^(\$third-party)?\r?$/ {
        line = $0
        sub(/^\|\|/, "", line)
        sub(/\^(\$third-party)?\r?$/, "", line)
        print tolower(line)
    }
' "$TEMP_DIR/source.txt" | LC_ALL=C sort -u > "$TEMP_DIR/hosts.txt"

# Current System WebView exposes neither ABP resource type nor the complete initiator chain.
# Preserve positive $domain= exceptions as request/page host pairs. This is broader than an
# upstream $script/$image exception, but only on the explicitly named page and therefore avoids
# known site breakage without globally removing the request host from protection.
awk '
    NR == FNR {
        blocked[$1] = 1
        next
    }
    /^@@\|\|[A-Za-z0-9.-]+\^(\$|$)/ {
        rule = substr($0, 5)
        match(rule, /^[A-Za-z0-9.-]+/)
        request_host = tolower(substr(rule, RSTART, RLENGTH))
        if (!(request_host in blocked)) next

        options_start = index($0, "$")
        if (options_start == 0) {
            print request_host "\t*"
            next
        }

        option_count = split(substr($0, options_start + 1), options, ",")
        domain_option = ""
        for (option_index = 1; option_index <= option_count; option_index++) {
            if (options[option_index] ~ /^domain=/) {
                domain_option = substr(options[option_index], 8)
            }
        }
        if (domain_option == "" || domain_option ~ /~|\*/) next

        domain_count = split(domain_option, domains, "|")
        for (domain_index = 1; domain_index <= domain_count; domain_index++) {
            page_host = tolower(domains[domain_index])
            if (page_host ~ /^[A-Za-z0-9.-]+$/) {
                print request_host "\t" page_host
            }
        }
    }
' "$TEMP_DIR/hosts.txt" "$TEMP_DIR/allow_source.txt" | LC_ALL=C sort -u \
    > "$TEMP_DIR/allowed_host_pairs.txt"

HOST_COUNT=$(wc -l < "$TEMP_DIR/hosts.txt" | tr -d ' ')
if [ "$HOST_COUNT" -lt 50000 ]; then
    echo "Refusing to replace content filters: only $HOST_COUNT hosts found" >&2
    exit 1
fi
ALLOW_PAIR_COUNT=$(wc -l < "$TEMP_DIR/allowed_host_pairs.txt" | tr -d ' ')
if [ "$ALLOW_PAIR_COUNT" -lt 50 ]; then
    echo "Refusing to replace content filters: only $ALLOW_PAIR_COUNT allow pairs found" >&2
    exit 1
fi

{
    printf '%s\n' '# Generated EasyList/EasyPrivacy host rules. Do not edit by hand.'
    printf '%s\n' '# Source: https://github.com/easylist/easylist'
    printf '# Source revision: %s\n' "$SOURCE_REVISION"
    printf '%s\n' '# License: CC BY-SA 3.0 or later; see content_filter.LICENSE.txt'
    printf '# Generated hosts: %s\n' "$HOST_COUNT"
    cat "$TEMP_DIR/hosts.txt"
} > "$TEMP_DIR/output.txt"

{
    printf '%s\n' '# Generated EasyList/EasyPrivacy domain-scoped allow exceptions. Do not edit by hand.'
    printf '%s\n' '# Format: request-host<TAB>page-host'
    printf '%s\n' '# Source: https://github.com/easylist/easylist'
    printf '# Source revision: %s\n' "$SOURCE_REVISION"
    printf '%s\n' '# License: CC BY-SA 3.0 or later; see content_filter.LICENSE.txt'
    printf '# Generated host pairs: %s\n' "$ALLOW_PAIR_COUNT"
    cat "$TEMP_DIR/allowed_host_pairs.txt"
} > "$TEMP_DIR/allow_output.txt"

chmod 644 "$TEMP_DIR/output.txt"
chmod 644 "$TEMP_DIR/allow_output.txt"
mv "$TEMP_DIR/output.txt" "$OUTPUT_FILE"
mv "$TEMP_DIR/allow_output.txt" "$ALLOW_OUTPUT_FILE"

echo "Wrote $HOST_COUNT hosts and $ALLOW_PAIR_COUNT allow pairs"
