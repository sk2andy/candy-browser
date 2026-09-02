#!/bin/sh
set -eu

sync_dir=/workspace/sync
database_path=/tmp/candy-sync-e2e.sqlite3
server_log=/tmp/candy-sync-e2e.log
server_pid=

cleanup() {
    if [ -n "$server_pid" ]; then
        kill "$server_pid" >/dev/null 2>&1 || true
        wait "$server_pid" >/dev/null 2>&1 || true
    fi
}
trap cleanup EXIT INT TERM

cd "$sync_dir/server"
go test -race -count=1 ./...
go vet ./...
go build -trimpath -buildvcs=false -o /tmp/candy-sync ./cmd/candy-sync

cd "$sync_dir/extension"
npm run verify

CANDY_SYNC_USERNAME=candy \
CANDY_SYNC_PASSWORD=integration-password-123 \
CANDY_SYNC_LISTEN_ADDR=127.0.0.1:18081 \
CANDY_SYNC_DB_PATH="$database_path" \
    /tmp/candy-sync >"$server_log" 2>&1 &
server_pid=$!

attempt=0
until curl --fail --silent http://127.0.0.1:18081/readyz >/dev/null; do
    attempt=$((attempt + 1))
    if [ "$attempt" -ge 50 ]; then
        sed -n '1,200p' "$server_log"
        exit 1
    fi
    sleep 0.1
done

CANDY_SYNC_E2E_URL=http://127.0.0.1:18081/ \
CANDY_SYNC_E2E_USERNAME=candy \
CANDY_SYNC_E2E_PASSWORD=integration-password-123 \
    npm test

kill "$server_pid"
wait "$server_pid" || true
server_pid=

for canary in \
    integration-only-recovery-passphrase \
    plaintext-canary.invalid \
    PLAINTEXT_SYNC_CANARY \
    second-canary.invalid \
    SECOND_SYNC_CANARY \
    'E2E Device' \
    'Recovered E2E Device' \
    '"schemaVersion":1' \
    '"kind":"desktop"' \
    '"kind":"laptop"' \
    '"accentHue":' \
    '"glyphVariant":'; do
    if grep -a -F "$canary" "$database_path" "$database_path-wal" "$database_path-shm" "$server_log" 2>/dev/null; then
        echo "secret/plaintext canary leaked into server persistence or logs: $canary" >&2
        exit 1
    fi
done

echo "Candy Sync full isolated verification passed"
