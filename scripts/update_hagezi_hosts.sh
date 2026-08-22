#!/bin/sh
set -eu

SOURCE_REVISION="4952c89a3ee5e87e173cef9c6c21a17345dfdc24"
SOURCE_SHA256="9d9737fb46df0b9a5cdf08a8497c45a4ac8f0d106b3e45269098f65b89e24df0"
LICENSE_SHA256="3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986"
SOURCE_URL="https://raw.githubusercontent.com/hagezi/dns-blocklists/$SOURCE_REVISION/adblock/pro.txt"
LICENSE_URL="https://raw.githubusercontent.com/hagezi/dns-blocklists/$SOURCE_REVISION/LICENSE"
PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ASSET_DIR="$PROJECT_DIR/app/src/main/assets"
TEMP_DIR=$(mktemp -d)
trap 'rm -rf "$TEMP_DIR"' EXIT HUP INT TERM

# HaGeZi is a delta against these generated/runtime assets. Refresh EasyList and uAssets first
# whenever their pinned revisions change, then regenerate this asset in the same build update.

curl --fail --location --silent --show-error "$SOURCE_URL" > "$TEMP_DIR/source.txt"
curl --fail --location --silent --show-error "$LICENSE_URL" > "$TEMP_DIR/license.txt"
ACTUAL_LICENSE_SHA256=$(sha256sum "$TEMP_DIR/license.txt" | cut -d ' ' -f 1)
if [ "$ACTUAL_LICENSE_SHA256" != "$LICENSE_SHA256" ]; then
    echo "HaGeZi license SHA-256 mismatch: $ACTUAL_LICENSE_SHA256" >&2
    exit 1
fi

python3 "$PROJECT_DIR/scripts/compile_hagezi_hosts.py" \
    --source-file "$TEMP_DIR/source.txt" \
    --source-sha256 "$SOURCE_SHA256" \
    --revision "$SOURCE_REVISION" \
    --exclude-file "$ASSET_DIR/easylist_blocked_hosts.txt" \
    --exclude-file "$ASSET_DIR/blocked_hosts.txt" \
    --exclude-file "$ASSET_DIR/uassets_blocked_hosts.txt" \
    --output "$TEMP_DIR/hagezi_blocked_hosts.txt"

{
    printf '%s\n' 'HaGeZi Pro attribution and license'
    printf '%s\n' '================================='
    printf '\n'
    printf 'Source: %s\n' "$SOURCE_URL"
    printf 'Source revision: %s\n' "$SOURCE_REVISION"
    printf 'Source SHA-256: %s\n' "$SOURCE_SHA256"
    printf 'License SHA-256: %s\n' "$LICENSE_SHA256"
    printf '%s\n' 'Upstream project: https://github.com/hagezi/dns-blocklists'
    printf '%s\n' 'License: GNU General Public License version 3'
    printf '\n'
    printf '%s\n' 'Modification notice:'
    printf '%s\n' '- Exact host rules are validated, normalized, sorted, and deduplicated.'
    printf '%s\n' '- Hosts already covered by Candy built-in, EasyList, or uAssets host rules are excluded.'
    printf '%s\n' '- scripts/update_hagezi_hosts.sh and scripts/compile_hagezi_hosts.py are the transformation source.'
    printf '\n'
    cat "$TEMP_DIR/license.txt"
} > "$TEMP_DIR/hagezi.LICENSE.txt"

chmod 644 "$TEMP_DIR/hagezi_blocked_hosts.txt" "$TEMP_DIR/hagezi.LICENSE.txt"
mv "$TEMP_DIR/hagezi_blocked_hosts.txt" "$ASSET_DIR/hagezi_blocked_hosts.txt"
mv "$TEMP_DIR/hagezi.LICENSE.txt" "$ASSET_DIR/hagezi.LICENSE.txt"
