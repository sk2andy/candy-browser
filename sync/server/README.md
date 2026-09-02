# Candy Sync self-hosted server

Single-workspace Go server for Candy Sync protocol v1. It stores encrypted payloads and only the
routing metadata needed for cursor sync, device revocation, and target-device tab profiles.

## Security boundary

The server never receives the E2EE passphrase, workspace key, device private keys, device names,
device icon descriptors, URLs, titles, or other sync plaintext. `CANDY_SYNC_PASSPHRASE` causes an
explicit startup failure. Putting that value in Docker or server environment variables would reduce
E2EE to server-side encryption.

Device private keys and the workspace key are generated on client devices with a CSPRNG. The
immutable passphrase encrypts those client-side secrets. Losing the passphrase prevents recovery on
a new device. Protocol v1 intentionally has no passphrase-change or recovery-envelope replacement
operation.

The server does see workspace/device/entity identifiers, operations, revisions, ciphertext sizes,
timestamps, cursors, IP addresses, and request frequency. Revoking a device prevents future server
access but cannot remove plaintext already copied to that device.

## Run with Docker Compose

Copy `.env.example` to an ignored `.env`, replace both credentials, then run:

```sh
docker compose up --build -d
docker compose ps
```

Compose starts the Go service behind a Caddy gateway. The gateway exposes HTTPS on
`https://localhost:8443` and keeps HTTP on `http://localhost:8080` for explicit local-development
use. Caddy creates and renews a leaf certificate through a persistent local CA. Export its public
root certificate after the first start:

```sh
./scripts/export-local-ca.sh
```

Trust that certificate only on development clients that connect to this server. The CA private key
stays in the `candy-sync-tls-data` Docker volume; deleting that volume rotates the CA and requires
clients to trust the new root. Never export or distribute the CA private key.

On macOS, import `.local-tls/root.crt` into the login keychain and explicitly trust it for SSL in
Keychain Access. On Android, install the root as a user CA and use Candy Browser's opt-in
`userCaDebug` or `userCaRelease` variant. The standard app deliberately trusts only system CAs.
Importing a local CA is a security-sensitive client action: trust only this exported certificate,
protect the Docker volume that contains its signing key, and remove the trust when local HTTPS is
no longer needed.

For LAN access, set `CANDY_SYNC_TLS_HOST` to the exact IP address or DNS name used by clients,
`CANDY_SYNC_PUBLIC_URL` to the corresponding HTTPS URL, and `CANDY_SYNC_BIND_ADDRESS=0.0.0.0` (or a
specific host address). Trusted-LAN cleartext access must additionally set
`CANDY_SYNC_PUBLIC_URL=http://...`, `CANDY_SYNC_ALLOW_HTTP=true`, and
use the HTTP port. Never expose HTTP to an untrusted network: E2EE hides tab content, but HTTP
exposes Basic credentials, bearer tokens, and traffic metadata.

SQLite lives at `/data/candy-sync.sqlite3`, uses WAL and `synchronous=FULL`, and must reside on a
local filesystem. Do not use NFS or run multiple server replicas against the same SQLite file.

## Configuration

| Variable | Required | Default | Purpose |
| --- | ---: | --- | --- |
| `CANDY_SYNC_USERNAME` | yes | - | Bootstrap/enrollment username |
| `CANDY_SYNC_PASSWORD` | yes | - | At least 16 bytes; changing it invalidates all device tokens |
| `CANDY_SYNC_LISTEN_ADDR` | no | `:8080` | HTTP listen address |
| `CANDY_SYNC_DB_PATH` | no | `/data/candy-sync.sqlite3` | SQLite file on local storage |
| `CANDY_SYNC_PUBLIC_URL` | no | - | HTTPS public origin, or explicitly allowed HTTP origin |
| `CANDY_SYNC_ALLOW_HTTP` | no | `false` | Permit and advertise non-loopback HTTP to clients |
| `CANDY_SYNC_BIND_ADDRESS` | no | `127.0.0.1` | Host address used by the supplied Compose port mapping |
| `CANDY_SYNC_PORT` | no | `8080` | Host port for the optional HTTP gateway |
| `CANDY_SYNC_TLS_HOST` | no | `localhost` | Exact DNS name or IP address included in the local TLS certificate |
| `CANDY_SYNC_TLS_PORT` | no | `8443` | Host port for the HTTPS gateway |
| `CANDY_SYNC_TOKEN_TTL` | no | `0` | Device token TTL; `0` means until revocation |
| `CANDY_SYNC_MAX_BODY_BYTES` | no | `1048576` | Request body limit |
| `CANDY_SYNC_MAX_BATCH` | no | `250` | Push/pull batch limit |
| `CANDY_SYNC_LOG_LEVEL` | no | `info` | `debug`, `info`, `warn`, or `error` |
| `CANDY_SYNC_CLIENT_IP_HEADER` | no | empty | Trusted reverse-proxy header for per-client Basic-auth limits |

Username/password are used for authenticated bootstrap and enrollment. The client exchanges them for
a random, device-scoped bearer token and must not persist the password. The database stores only a
keyed token hash. Changing the configured username or password invalidates existing tokens.

Basic-auth attempts are limited per client address. The supplied Caddy gateway is the only
published service and overwrites `X-Forwarded-For`; the Go service is reachable only inside the
Compose network. Another reverse proxy must provide the same boundary. Without a trusted proxy, leave
`CANDY_SYNC_CLIENT_IP_HEADER` empty. Missing or malformed header values fall back
to the transport peer.

## API

| Method | Path | Authentication |
| --- | --- | --- |
| `GET` | `/.well-known/candy-sync` | none |
| `GET` | `/healthz` | none |
| `GET` | `/readyz` | none |
| `GET` | `/v1/bootstrap` | Basic |
| `POST` | `/v1/devices` | Basic |
| `GET` | `/v1/devices` | Bearer |
| `DELETE` | `/v1/devices/{id}` | Basic re-authentication |
| `POST` | `/v1/sync/push` | Bearer + `Idempotency-Key` |
| `GET` | `/v1/sync/pull?after={cursor}` | Bearer |
| `POST` | `/v1/sync/ack` | Bearer |
| `GET` | `/v1/sync/snapshot` | Bearer |
| `PUT` | `/v1/devices/{targetId}/tabs` | active device Bearer + `Idempotency-Key` |

API errors use `application/problem+json`. Revisions are decimal strings to avoid JavaScript integer
loss. Cursors are opaque `serverEpoch.sequence` values. A cursor from another epoch or ahead of the
database returns `410 cursor_reset`; clients must fetch a full encrypted snapshot.

Enrollment requires an encrypted name and encrypted device-icon descriptor. Device listing returns
both opaque envelopes. Databases migrated from the pre-icon schema may return `encryptedIcon: null`
for legacy rows; new enrollment always requires the field.

Every push is one SQLite transaction. Duplicate `(deviceId, changeId)` with identical encrypted
envelope is a successful retry; different content returns `409 idempotency_conflict`. Shared objects
and tab snapshots use compare-and-swap revisions.

Any active workspace device may update another active device's synced tab profile.
The bearer-authenticated writer remains in change metadata as `deviceId`; the target
profile is `entityId`. This is auditable routing metadata only—the payload stays E2EE.

## Tests

Local toolchain:

```sh
go test -count=1 ./...
go test -race -count=1 ./...
go vet ./...
```

Docker-only reproducible gate:

```sh
./scripts/test.sh
./scripts/test-compose-https.sh
```

The container test target downloads pinned Go modules, runs all tests with the race detector, and
runs `go vet`. It requires no database, port, credentials, or other manual setup.

## Backup and restore

Use a SQLite-consistent backup, not a live filesystem copy that omits WAL contents. Stop the server
before a simple file copy. A restored older database must receive a new server epoch before clients
resume; until a dedicated restore command exists, create a fresh workspace and encrypted full sync.
