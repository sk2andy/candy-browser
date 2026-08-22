#!/bin/sh
set -eu

# Compile Candy's host-safe network subset from the complete pinned EasyList and EasyPrivacy
# template graph. Resource-typed block rules remain excluded because System WebView does not
# expose a reliable request type; positive domain-scoped allows retain the existing broader
# page-host exception semantics.
SOURCE_REVISION="54849f55642f155a67649b46fe3b87c39607c1c5"
SOURCE_ARCHIVE="https://github.com/easylist/easylist/archive/$SOURCE_REVISION.tar.gz"
PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUTPUT_FILE="$PROJECT_DIR/app/src/main/assets/easylist_blocked_hosts.txt"
ALLOW_OUTPUT_FILE="$PROJECT_DIR/app/src/main/assets/easylist_allowed_host_pairs.txt"
TEMP_DIR=$(mktemp -d)
trap 'rm -rf "$TEMP_DIR"' EXIT HUP INT TERM

curl --fail --location --silent --show-error "$SOURCE_ARCHIVE" > "$TEMP_DIR/source.tar.gz"
tar -xzf "$TEMP_DIR/source.tar.gz" -C "$TEMP_DIR"

python3 "$PROJECT_DIR/scripts/compile_easylist_hosts.py" \
    --source-root "$TEMP_DIR/easylist-$SOURCE_REVISION" \
    --template easylist.template \
    --template easyprivacy.template \
    --revision "$SOURCE_REVISION" \
    --blocked-output "$TEMP_DIR/easylist_blocked_hosts.txt" \
    --allowed-output "$TEMP_DIR/easylist_allowed_host_pairs.txt"

chmod 644 \
    "$TEMP_DIR/easylist_blocked_hosts.txt" \
    "$TEMP_DIR/easylist_allowed_host_pairs.txt"
mv "$TEMP_DIR/easylist_blocked_hosts.txt" "$OUTPUT_FILE"
mv "$TEMP_DIR/easylist_allowed_host_pairs.txt" "$ALLOW_OUTPUT_FILE"
