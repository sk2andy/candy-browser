#!/bin/sh
set -eu

# Compile only domain-specific standard CSS from EasyList/EasyPrivacy. Arc assets and code are
# intentionally not inputs. Source revision is pinned so builds and tests remain reproducible.
SOURCE_REVISION="f1f8fe7c4bedf834bb89a7631b9cae94f627b263"
SOURCE_ARCHIVE="https://github.com/easylist/easylist/archive/$SOURCE_REVISION.tar.gz"
PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUTPUT_FILE="$PROJECT_DIR/app/src/main/assets/easylist_cosmetic_rules.txt"
TEMP_DIR=$(mktemp -d)
trap 'rm -rf "$TEMP_DIR"' EXIT HUP INT TERM

curl --fail --location --silent --show-error "$SOURCE_ARCHIVE" > "$TEMP_DIR/source.tar.gz"
tar -xzf "$TEMP_DIR/source.tar.gz" -C "$TEMP_DIR"

python3 "$PROJECT_DIR/scripts/compile_easylist_cosmetic.py" \
    --source-root "$TEMP_DIR/easylist-$SOURCE_REVISION" \
    --template easylist.template \
    --template easyprivacy.template \
    --revision "$SOURCE_REVISION" \
    --output "$TEMP_DIR/easylist_cosmetic_rules.txt"

chmod 644 "$TEMP_DIR/easylist_cosmetic_rules.txt"
mv "$TEMP_DIR/easylist_cosmetic_rules.txt" "$OUTPUT_FILE"
