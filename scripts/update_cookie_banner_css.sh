#!/bin/sh
set -eu

SOURCE_REVISION="ccad5d19389d41145cbae02226755c2e37e2343c"
SOURCE_URL="https://raw.githubusercontent.com/easylist/easylist/$SOURCE_REVISION/easylist_cookie/easylist_cookie_general_hide.txt"
PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUTPUT_FILE="$PROJECT_DIR/app/src/main/assets/easylist_cookie_banner.css"
TEMP_DIR=$(mktemp -d)
trap 'rm -rf "$TEMP_DIR"' EXIT HUP INT TERM

curl --fail --location --silent --show-error "$SOURCE_URL" > "$TEMP_DIR/source.txt"

SELECTOR_COUNT=$(awk '/^##/ && !/^#@#/ { count += 1 } END { print count + 0 }' "$TEMP_DIR/source.txt")
if [ "$SELECTOR_COUNT" -lt 10000 ]; then
    echo "Refusing to replace cookie CSS: only $SELECTOR_COUNT selectors found" >&2
    exit 1
fi

{
    printf '%s\n' '/*!'
    printf '%s\n' ' * EasyList Cookie List - general element hiding rules'
    printf '%s\n' ' * Source: https://github.com/easylist/easylist'
    printf ' * Source revision: %s\n' "$SOURCE_REVISION"
    printf '%s\n' ' * Authors: The EasyList authors (https://easylist.to/)'
    printf '%s\n' ' * License: Creative Commons Attribution-ShareAlike 3.0 Unported or later'
    printf '%s\n' ' * https://creativecommons.org/licenses/by-sa/3.0/'
    printf '%s\n' ' * Full attribution: app/src/main/assets/content_filter.LICENSE.txt'
    printf '%s\n' ' * Modified by compiling generic Adblock Plus element-hiding rules into CSS.'
    printf ' * Generated selectors: %s\n' "$SELECTOR_COUNT"
    printf '%s\n' ' */'
    awk '
        function close_rule() {
            print ":root:not(:root) {"
            print "    display: none !important;"
            print "    height: 0 !important;"
            print "    z-index: -99999 !important;"
            print "    visibility: hidden !important;"
            print "    width: 0 !important;"
            print "    overflow: hidden !important;"
            print "}"
            print ""
        }
        /^##/ && !/^#@#/ {
            sub(/^##/, "")
            sub(/\r$/, "")
            print $0 ","
            chunk_size += 1
            if (chunk_size == 128) {
                close_rule()
                chunk_size = 0
            }
        }
        END {
            if (chunk_size > 0) close_rule()
        }
    ' "$TEMP_DIR/source.txt"
} > "$TEMP_DIR/output.css"

chmod 644 "$TEMP_DIR/output.css"
mv "$TEMP_DIR/output.css" "$OUTPUT_FILE"

echo "Wrote $SELECTOR_COUNT selectors to $OUTPUT_FILE"
