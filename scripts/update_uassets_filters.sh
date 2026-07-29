#!/bin/sh
set -eu

# Compile only the exact network semantics supported by Candy from uBlock Origin's
# supplementary Ads list. The complete pinned source and GPL-3.0 license are shipped beside
# the generated files so recipients receive the corresponding source for this transformation.
SOURCE_REVISION="05bc031ad40c2270223f068f052970201ca1bf14"
SOURCE_ROOT="https://raw.githubusercontent.com/uBlockOrigin/uAssets/$SOURCE_REVISION"
SOURCE_URL="$SOURCE_ROOT/filters/filters.txt"
LICENSE_URL="$SOURCE_ROOT/LICENSE"
PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ASSET_DIR="$PROJECT_DIR/app/src/main/assets"
TEMP_DIR=$(mktemp -d)
trap 'rm -rf "$TEMP_DIR"' EXIT HUP INT TERM

curl --fail --location --silent --show-error "$SOURCE_URL" > "$TEMP_DIR/source.txt"
curl --fail --location --silent --show-error "$LICENSE_URL" > "$TEMP_DIR/license.txt"

# Match Candy's deliberately small ABP/uBlock network subset:
#   ||request-host^
#   ||request-host^$domain=page-host|other-page-host[,3p]
# and their @@ allow forms. Conditional sections and every other modifier are excluded.
awk '
    BEGIN { conditional_depth = 0 }
    /^!#if/ {
        conditional_depth += 1
        next
    }
    /^!#endif/ {
        if (conditional_depth > 0) conditional_depth -= 1
        next
    }
    conditional_depth > 0 { next }
    {
        line = $0
        sub(/\r$/, "", line)
        action = "block"
        if (line ~ /^@@/) {
            action = "allow"
            sub(/^@@/, "", line)
        }
        if (line !~ /^\|\|[A-Za-z0-9.-]+\^/) next

        part_count = split(line, parts, "\\$")
        pattern = parts[1]
        if (pattern !~ /^\|\|[A-Za-z0-9.-]+\^$/) next
        request_host = pattern
        sub(/^\|\|/, "", request_host)
        sub(/\^$/, "", request_host)
        request_host = tolower(request_host)

        if (part_count == 1) {
            print action "\thost\t" request_host
            next
        }
        option_count = split(parts[2], options, ",")
        domain_option = ""
        valid = 1
        for (option_index = 1; option_index <= option_count; option_index++) {
            option = options[option_index]
            if (option ~ /^(domain|from)=/) {
                if (domain_option != "") valid = 0
                domain_option = option
                sub(/^(domain|from)=/, "", domain_option)
            } else if (option != "3p" && option != "third-party") {
                valid = 0
            }
        }
        if (!valid || domain_option == "" || domain_option ~ /[~*]/) next

        domain_count = split(domain_option, domains, "\\|")
        for (domain_index = 1; domain_index <= domain_count; domain_index++) {
            page_host = tolower(domains[domain_index])
            if (page_host ~ /^[A-Za-z0-9.-]+$/) {
                print action "\tpair\t" request_host "\t" page_host
            }
        }
    }
' "$TEMP_DIR/source.txt" | LC_ALL=C sort -u > "$TEMP_DIR/rules.txt"

awk -F '\t' '$1 == "block" && $2 == "host" { print $3 }' \
    "$TEMP_DIR/rules.txt" > "$TEMP_DIR/blocked_hosts.txt"
awk -F '\t' '$1 == "block" && $2 == "pair" { print $3 "\t" $4 }' \
    "$TEMP_DIR/rules.txt" > "$TEMP_DIR/blocked_pairs.txt"
awk -F '\t' '
    $1 == "allow" && $2 == "host" { print $3 "\t*" }
    $1 == "allow" && $2 == "pair" { print $3 "\t" $4 }
' "$TEMP_DIR/rules.txt" > "$TEMP_DIR/allowed_pairs.txt"

BLOCK_HOST_COUNT=$(wc -l < "$TEMP_DIR/blocked_hosts.txt" | tr -d ' ')
BLOCK_PAIR_COUNT=$(wc -l < "$TEMP_DIR/blocked_pairs.txt" | tr -d ' ')
ALLOW_PAIR_COUNT=$(wc -l < "$TEMP_DIR/allowed_pairs.txt" | tr -d ' ')
TOTAL_COUNT=$((BLOCK_HOST_COUNT + BLOCK_PAIR_COUNT + ALLOW_PAIR_COUNT))
if [ "$BLOCK_HOST_COUNT" -lt 35 ] || [ "$BLOCK_PAIR_COUNT" -lt 5 ] || \
    [ "$ALLOW_PAIR_COUNT" -lt 1 ] || [ "$TOTAL_COUNT" -gt 256 ]; then
    echo "Refusing uAssets update: unexpected supported rule counts " \
        "$BLOCK_HOST_COUNT/$BLOCK_PAIR_COUNT/$ALLOW_PAIR_COUNT" >&2
    exit 1
fi

write_header() {
    description=$1
    count=$2
    printf '# Generated %s. Do not edit by hand.\n' "$description"
    printf '# Source: %s\n' "$SOURCE_URL"
    printf '# Source revision: %s\n' "$SOURCE_REVISION"
    printf '%s\n' '# License: GPL-3.0; see uassets.LICENSE.txt'
    printf '# Generated rules: %s\n' "$count"
}

{
    write_header "uAssets blocked hosts" "$BLOCK_HOST_COUNT"
    cat "$TEMP_DIR/blocked_hosts.txt"
} > "$TEMP_DIR/uassets_blocked_hosts.txt"
{
    write_header "uAssets first-party-to-blocked-host pairs" "$BLOCK_PAIR_COUNT"
    printf '%s\n' '# Format: request-host<TAB>page-host'
    cat "$TEMP_DIR/blocked_pairs.txt"
} > "$TEMP_DIR/uassets_blocked_host_pairs.txt"
{
    write_header "uAssets allow exceptions" "$ALLOW_PAIR_COUNT"
    printf '%s\n' '# Format: request-host<TAB>page-host; * means every page host'
    cat "$TEMP_DIR/allowed_pairs.txt"
} > "$TEMP_DIR/uassets_allowed_host_pairs.txt"
{
    printf '%s\n' 'uBlock Origin uAssets attribution and license'
    printf '%s\n' '============================================'
    printf '%s\n' ''
    printf '%s\n' 'Candy Browser contains a modified, compiled subset of network rules from uAssets.'
    printf 'Source: %s\n' "$SOURCE_URL"
    printf 'Source revision: %s\n' "$SOURCE_REVISION"
    printf '%s\n' 'Upstream project: https://github.com/uBlockOrigin/uAssets'
    printf '%s\n' 'License: GNU General Public License version 3'
    printf '%s\n' ''
    printf '%s\n' 'Modification notice:'
    printf '%s\n' '- Exact host and positive site-to-host rules supported by Candy are compiled into plain lists.'
    printf '%s\n' '- Paths, resource modifiers, regexes, redirects, scriptlets, JavaScript, and cosmetic rules are excluded.'
    printf '%s\n' '- The complete pinned input is shipped as uassets_filters_source.txt.'
    printf '%s\n' '- scripts/update_uassets_filters.sh is the corresponding transformation source.'
    printf '%s\n' ''
    cat "$TEMP_DIR/license.txt"
} > "$TEMP_DIR/uassets.LICENSE.txt"

chmod 644 "$TEMP_DIR"/uassets_*.txt "$TEMP_DIR/uassets.LICENSE.txt"
mv "$TEMP_DIR/uassets_blocked_hosts.txt" "$ASSET_DIR/uassets_blocked_hosts.txt"
mv "$TEMP_DIR/uassets_blocked_host_pairs.txt" "$ASSET_DIR/uassets_blocked_host_pairs.txt"
mv "$TEMP_DIR/uassets_allowed_host_pairs.txt" "$ASSET_DIR/uassets_allowed_host_pairs.txt"
mv "$TEMP_DIR/uassets.LICENSE.txt" "$ASSET_DIR/uassets.LICENSE.txt"
mv "$TEMP_DIR/source.txt" "$ASSET_DIR/uassets_filters_source.txt"

echo "Wrote $BLOCK_HOST_COUNT hosts, $BLOCK_PAIR_COUNT block pairs, and $ALLOW_PAIR_COUNT allow pairs"
