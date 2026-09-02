#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
server_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)
output_path=${1:-"$server_dir/candy-sync-local-ca.crt"}
container_id=$(docker compose --file "$server_dir/compose.yaml" ps --quiet candy-sync-tls)

if [ -z "$container_id" ]; then
    echo "Candy Sync TLS is not running. Start it with: docker compose up --build -d" >&2
    exit 1
fi

docker cp "$container_id:/data/caddy/pki/authorities/local/root.crt" "$output_path" >/dev/null
chmod 0644 "$output_path"

echo "Exported the Candy Sync local CA certificate to: $output_path"
openssl x509 -in "$output_path" -noout -sha256 -fingerprint
