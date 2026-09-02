# Candy Sync protocols

This directory is the shared wire contract for implemented clients and Go server.
Protocol v1 remains the encrypted-snapshot compatibility contract. Protocol v2 adds
workspace-scoped encrypted tab mutations with durable REST recovery and WebSocket delivery.

| File | Purpose |
| --- | --- |
| [`openapi.yaml`](openapi.yaml) | REST API, auth boundary, idempotency and cursor contracts |
| [`device-icons-v1.json`](device-icons-v1.json) | Shared selectable profile-icon ids, emoji glyphs, and labels for Android and WebExtension clients |
| [`schemas/encrypted-change-v1.schema.json`](schemas/encrypted-change-v1.schema.json) | Encrypted tab snapshot changes used by push/pull |
| [`schemas/device-v1.schema.json`](schemas/device-v1.schema.json) | P-256 public identity, encrypted name/icon records, and enrollment request |
| [`schemas/bootstrap-v1.schema.json`](schemas/bootstrap-v1.schema.json) | Basic-auth bootstrap response, Argon2id contract and recovery envelope |
| [`schemas/tab-snapshot-v1.schema.json`](schemas/tab-snapshot-v1.schema.json) | Encrypted target-device tab profiles and writer metadata |
| [`schemas/tab-snapshot-payload-v1.schema.json`](schemas/tab-snapshot-payload-v1.schema.json) | Client-only plaintext serialized before encryption |
| [`schemas/encrypted-tab-delta-v2.schema.json`](schemas/encrypted-tab-delta-v2.schema.json) | Authenticated routing envelope for encrypted tab mutations |
| [`schemas/tab-mutation-payload-v2.schema.json`](schemas/tab-mutation-payload-v2.schema.json) | Strict client-only plaintext for open, navigate, close, reorder, and set-pinned mutations |
| [`fixtures/`](fixtures/) | Small structural valid/invalid examples; never cryptographic expected values |
| [`../SECURITY.md`](../SECURITY.md) | Normative crypto, local storage and threat model contract |

Protocol and crypto versions are independent. Clients fail closed when either is
unknown. Non-negative signed 64-bit revisions travel as decimal strings to avoid
JavaScript precision loss; cursor is opaque `serverEpoch.sequence` string.

Every v1 push contains exactly one change and its `Idempotency-Key` equals that
change's `changeId`. Durable retry reuses the change ID, nonce and ciphertext.
The extension rejects JSON responses above 1,048,576 bytes, including chunked
bodies. Pull paginates below that ceiling; an oversized snapshot fails with HTTP
`413` and must be recovered through paginated pull.

Implemented enrollment supports first-device initialization and later-device
recovery with the same passphrase. Direct device pairing without passphrase, ECDH
workspace-key envelopes, signatures and key rotation are deliberately absent from
OpenAPI v1 and documented as later work in [`../SECURITY.md`](../SECURITY.md).

New enrollment requires an E2EE device-icon envelope. Its plaintext descriptor is
limited to `schemaVersion`, a `catalogId` from `device-icons-v1.json`, and `accentHue`; the synced-profile
badge remains local client UI state. A migrated legacy row can expose `encryptedIcon`
as `null`, for which clients render a generic fallback. Device-name and icon keys plus
AAD bind the SHA-256 fingerprint of the record's canonical P-256 SPKI, so moving either
envelope to another device record fails authentication.

An authenticated active device may CAS-update another active device's tab profile through
`PUT /v1/devices/{targetDeviceId}/tabs`. Pull records keep authenticated writer identity in
`deviceId` and target profile identity in `entityId`. Tab-payload keys derive from target
`entityId`; AEAD AAD binds writer and target, preventing metadata substitution.

Fixtures with `.valid.json` suffix validate against matching root schema; matching
`.invalid.json` fixtures fail. Repeated bytes have structural encoded length only.
They are not valid P-256 keys or AES outputs and must never be crypto test vectors.

## Protocol v2 realtime deltas

Clients persist a mutation in their local outbox, encrypt it, then send exactly one item to
`POST /v2/sync/push`. `workspaceId` and writer `deviceId` are claims only: the server derives both
from the bearer token and rejects mismatches. The server assigns target-profile `revision`, commits
the envelope and workspace cursor in one SQLite transaction, and only then broadcasts the same
envelope. Sender receives the broadcast too.

Plaintext follows `tab-mutation-payload-v2.schema.json`. `mutationId` and `targetDeviceId` occur
inside plaintext as an authenticated consistency check and in authenticated envelope metadata.
Clients must verify equality after decryption. The server has no plaintext endpoint and must never
receive this payload outside ciphertext.

V2 mutation plaintext is bounded to 196,608 UTF-8 bytes and ciphertext including the GCM tag to
196,624 bytes, encoded as at most 262,166 unpadded base64url characters. The limit accommodates the
maximum 1,000-entry reorder mutation and is identical in the schema, server, Android, and extension.

WebSocket delivery is an optimization, never the source of truth. A client detects a cursor gap,
socket loss, slow-consumer disconnect, or background wake by calling `GET /v2/sync/pull` from its
last durably applied v2 cursor. V1 and v2 cursors use the same server epoch but independent sequence
spaces; clients store them separately.

Realtime authentication exchanges a device bearer token for a 45-second, single-use ticket through
`POST /v2/realtime/tickets`. The ticket is consumed during `GET /v2/realtime?ticket=...`; clients
must not persist it. Realtime frames have this shape:

```json
{
  "type": "change",
  "cursor": "epoch_example.42",
  "change": {
    "changeId": "change_example",
    "mutationId": "mutation_example",
    "workspaceId": "workspace_example",
    "deviceId": "writer_device",
    "entity": "tabs",
    "entityId": "target_device",
    "operation": "delta",
    "baseRevision": "8",
    "revision": "9",
    "schemaVersion": 2,
    "cryptoVersion": 1,
    "keyVersion": 1,
    "nonce": "AAAAAAAAAAAAAAAA",
    "ciphertext": "AAAAAAAAAAAAAAAAAAAAAA"
  }
}
```

Server storage includes `accounts`, `workspace_members`, per-workspace v2 sequence heads, scoped
change uniqueness, and scoped tab-profile revisions. Current Basic credentials bootstrap only the
default account and workspace. Account provisioning, shared workspace invitations, roles beyond
stored membership, and workspace-key rotation remain a later protocol iteration.

Workspace `protocol_floor` starts at 1. First successfully committed v2 delta promotes it to 2 in
the same transaction. From then on, v1 tab snapshot writes return `409 protocol_upgrade_required`;
v1 reads remain available. Before promotion, every v1 tab write advances the corresponding v2
revision baseline. This prevents v1 and v2 writers from forking one target profile.
