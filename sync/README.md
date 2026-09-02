# Candy Sync

Self-hosted, browser-native synchronization for Candy Browser. Android, Chromium, and Firefox can
open, navigate, pin, reorder, and close end-to-end encrypted device tabs through a small Go/SQLite
server.

The canonical developer and operator documentation starts at
[`../docs/sync/README.md`](../docs/sync/README.md). Component-local documentation remains beside the
code for contributors working directly on the server, extension, or protocol.

## One-command verification

Prerequisite: Docker with Compose v2. No local Go, Node.js, npm, SQLite, credentials, ports, or manual setup are required.

```sh
./sync/scripts/test-all.sh
```

The isolated test image:

1. installs dependencies from the committed lock files;
2. runs Go tests with the race detector and `go vet`;
3. type-checks, tests, builds, audits, and reproducibly rebuilds both extension targets;
4. starts a fresh server and runs real extension cryptography against it;
5. verifies first- and second-device recovery, cross-device writes, target-side browser apply,
   idempotent retry, conflict recovery, revision advance, and bounded pull pagination;
6. fails if E2EE passphrase or tab plaintext canaries occur in SQLite or server logs.

The Android gate provisions and removes its own dedicated emulator:

```sh
./sync/scripts/test-android.sh
```

It installs the configured system image when needed, sets `ANDROID_SERIAL` explicitly, and runs the
sync unit, Compose, and Keystore security tests without using a physical device.

## Components

| Path | Responsibility |
| --- | --- |
| [`server/`](server/) | Dockerized Go API and SQLite ciphertext store |
| [`extension/`](extension/) | Popup-free Chromium MV3 and Firefox extension |
| [`protocol/`](protocol/) | Protocol v1, schemas, fixtures, and security contract |

## Documentation

| Need | Document |
| --- | --- |
| Architecture, status, and data flow | [`../docs/sync/README.md`](../docs/sync/README.md) |
| Deploy and operate the server | [`../docs/sync/server.md`](../docs/sync/server.md) |
| Build, load, and understand the extension | [`../docs/sync/extension.md`](../docs/sync/extension.md) |
| Integrate remote devices into Candy Browser | [`../docs/sync/app-integration.md`](../docs/sync/app-integration.md) |

The deployment password authenticates access to the server. The separate E2EE passphrase never leaves a client and must never be configured as a server environment variable. It cannot be changed in protocol v1; losing it makes the encrypted workspace unrecoverable. See [`SECURITY.md`](SECURITY.md).
