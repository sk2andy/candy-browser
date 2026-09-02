#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
server_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)
project_name="candy-sync-test-$$"

cleanup() {
    docker compose \
        --project-name "$project_name" \
        --file "$server_dir/compose.test.yaml" \
        down --volumes --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

docker compose \
    --project-name "$project_name" \
    --file "$server_dir/compose.test.yaml" \
    up --build --abort-on-container-exit --exit-code-from tests
