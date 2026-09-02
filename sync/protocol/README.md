# Candy Sync protocol v1

This directory is shared wire contract for implemented extension/Go-server
vertical slice. It documents only behavior present in current v1 code.

| File | Purpose |
| --- | --- |
| [`openapi.yaml`](openapi.yaml) | REST API, auth boundary, idempotency and cursor contracts |
| [`device-icons-v1.json`](device-icons-v1.json) | Shared selectable profile-icon ids, emoji glyphs, and labels for Android and WebExtension clients |
| [`schemas/encrypted-change-v1.schema.json`](schemas/encrypted-change-v1.schema.json) | Encrypted tab snapshot changes used by push/pull |
| [`schemas/device-v1.schema.json`](schemas/device-v1.schema.json) | P-256 public identity, encrypted name/icon records, and enrollment request |
| [`schemas/bootstrap-v1.schema.json`](schemas/bootstrap-v1.schema.json) | Basic-auth bootstrap response, Argon2id contract and recovery envelope |
| [`schemas/tab-snapshot-v1.schema.json`](schemas/tab-snapshot-v1.schema.json) | Encrypted target-device tab profiles and writer metadata |
| [`schemas/tab-snapshot-payload-v1.schema.json`](schemas/tab-snapshot-payload-v1.schema.json) | Client-only plaintext serialized before encryption |
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
