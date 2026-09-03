#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
server_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)
project_name="candy-sync-https-test-$$"
http_port=18082
https_port=18443
ca_path=$(mktemp)
ca_after_restart=$(mktemp)
http_discovery=$(mktemp)
https_discovery=$(mktemp)

cleanup() {
    rm -f "$ca_path" "$ca_after_restart" "$http_discovery" "$https_discovery"
    docker compose \
        --project-name "$project_name" \
        --file "$server_dir/compose.yaml" \
        down --volumes --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

export CANDY_SYNC_USERNAME=candy
export CANDY_SYNC_PASSWORD=integration-password-123
export CANDY_SYNC_PUBLIC_URL="https://localhost:$https_port"
export CANDY_SYNC_ALLOW_HTTP=false
export CANDY_SYNC_BIND_ADDRESS=127.0.0.1
export CANDY_SYNC_PORT=$http_port
export CANDY_SYNC_TLS_HOST=localhost
export CANDY_SYNC_TLS_PORT=$https_port

docker compose \
    --project-name "$project_name" \
    --file "$server_dir/compose.yaml" \
    up --build --detach --wait

curl --fail --silent "http://127.0.0.1:$http_port/healthz" >/dev/null

backend_container=$(docker compose \
    --project-name "$project_name" \
    --file "$server_dir/compose.yaml" \
    ps --quiet candy-sync)
if [ "$(docker inspect "$backend_container" --format '{{json .HostConfig.PortBindings}}')" != "{}" ]; then
    echo "The Go backend must not publish a host port" >&2
    exit 1
fi

tls_container=$(docker compose \
    --project-name "$project_name" \
    --file "$server_dir/compose.yaml" \
    ps --quiet candy-sync-tls)
docker cp "$tls_container:/data/caddy/pki/authorities/local/root.crt" "$ca_path" >/dev/null

curl --fail --silent --cacert "$ca_path" "https://localhost:$https_port/healthz" >/dev/null

if curl --fail --silent "https://localhost:$https_port/healthz" >/dev/null 2>&1; then
    echo "HTTPS unexpectedly succeeded without the generated CA" >&2
    exit 1
fi

curl --fail --silent "http://127.0.0.1:$http_port/.well-known/candy-sync" >"$http_discovery"
curl --fail --silent --cacert "$ca_path" "https://localhost:$https_port/.well-known/candy-sync" >"$https_discovery"
cmp "$http_discovery" "$https_discovery"
grep -F '"protocol":"candy-sync"' "$https_discovery" >/dev/null

openssl s_client \
    -connect "localhost:$https_port" \
    -servername localhost \
    -CAfile "$ca_path" </dev/null 2>/dev/null \
    | openssl x509 -noout -text \
    | grep -F 'DNS:localhost' >/dev/null

docker compose \
    --project-name "$project_name" \
    --file "$server_dir/compose.yaml" \
    restart candy-sync-tls >/dev/null
docker compose \
    --project-name "$project_name" \
    --file "$server_dir/compose.yaml" \
    up --detach --wait candy-sync-tls >/dev/null
docker cp "$tls_container:/data/caddy/pki/authorities/local/root.crt" "$ca_after_restart" >/dev/null
cmp "$ca_path" "$ca_after_restart"

echo "Candy Sync dual HTTP/HTTPS Compose verification passed"
