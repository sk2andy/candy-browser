# Candy Sync Extension Security

## Non-negotiable invariants

1. The E2EE passphrase never leaves the extension.
2. The passphrase is never stored in `storage.local`, `storage.sync`,
   `storage.session`, requests, or logs.
3. The server password authenticates only server bootstrap and enrollment and
   is never persisted.
4. The workspace key is generated locally with `crypto.getRandomValues`; each
   device identity is an independently generated Web Crypto ECDH P-256 key pair.
5. The passphrase does not create device identity. It encrypts local secrets and
   the workspace-key recovery envelope.
6. Plaintext user data is encrypted before every upload.
7. Private windows, `about:`, `chrome:`, `file:`, and extension pages never enter
   a snapshot.
8. Server responses and local ciphertext envelopes are size-bounded and
   validated.
9. Device name and icon descriptor are encrypted before enrollment; the synced
   badge is local UI state rather than remote metadata.

A passphrase in the Docker Compose environment would be known to the server and
therefore would not be an E2EE passphrase. Server configuration may contain the
username, authentication password, and a separate server secret, but never this
client secret. The server rejects `CANDY_SYNC_PASSPHRASE` if it is present.

## Key hierarchy

```text
Passphrase --Argon2id 65536/3/1--> Local KEK --AES-256-GCM--> Vault
                                                            ├── Workspace key
                                                            ├── Device private key (PKCS8)
                                                            ├── Device token
                                                            ├── Workspace ID
                                                            └── Device ID

Passphrase --Argon2id 65536/3/4--> Recovery KEK --AES-256-GCM--> Workspace key

Workspace key --HKDF-SHA-256(device, domain)--> Tabs payload key
Tabs payload key --AES-256-GCM--> Tab snapshot ciphertext

Workspace key --HKDF-SHA-256(workspace, name domain, SPKI fingerprint)--> Device name key
Device name key --AES-256-GCM--> Device name ciphertext

Workspace key --HKDF-SHA-256(workspace, icon domain, SPKI fingerprint)--> Device icon key
Device icon key --AES-256-GCM--> Device icon descriptor ciphertext
```

| Primitive | Parameters |
| --- | --- |
| Local-vault Argon2id | v19, 64 MiB, 3 iterations, parallelism 1, 16-byte salt, 32-byte output |
| Recovery Argon2id | v19, 64 MiB, 3 iterations, parallelism 4, 16-byte salt, 32-byte output |
| Vault, recovery, name, icon, and payload AEAD | AES-256-GCM, random 12-byte nonce, 128-bit tag appended to ciphertext |
| Payload, device-name, and device-icon keys | HKDF-SHA-256 with fixed domain-separation strings |
| Workspace key | 32 random bytes |
| Device identity | Web Crypto ECDH P-256; DER SPKI public key and PKCS8 private key |
| Fingerprint | SHA-256 over the exact DER SPKI bytes |
| Binary encoding | Base64url without padding |

KDF parameters are versioned in their envelopes. The extension requires the
exact v1 integer values before starting Argon2id. Recovery parameters arrive
from the server but are not tunable; any downgrade, fractional value, or value
outside the exact contract fails closed before memory allocation. The local
vault uses its independent fixed `65536/3/1` parameters.

AES-GCM AAD binds protocol, crypto, key, and schema versions; device, change, and
entity identity; operation; and base revision. Every new encryption uses a fresh
CSPRNG nonce. Durable outbox retries reuse the original change ID, nonce, and
ciphertext instead of encrypting again under the same nonce.

## Device identity and recovery

The extension creates every device key pair locally with
`crypto.subtle.generateKey`. It exports the public key as DER SubjectPublicKeyInfo
and the private key as PKCS8. Only the SPKI public key and its SHA-256 fingerprint
reach the server. PKCS8 bytes exist persistently only inside the encrypted local
vault.

On first enrollment, the extension generates a random 32-byte workspace key and
encrypts it into an immutable recovery envelope under the passphrase-derived
recovery key. The server stores this envelope as opaque ciphertext. A later
device downloads the envelope, decrypts it locally with the same passphrase,
creates its own independent P-256 identity, and enrolls without replacing the
recovery envelope.

The passphrase is immutable in protocol v1. There is no change endpoint or input
for changing it. Loss is unrecoverable. A complete workspace reset is a separate,
destructive operation and is not part of this vertical slice. Because the
server-stored recovery envelope permits offline guessing, users should choose a
high-entropy passphrase and must not reuse the server-authentication password.

The icon plaintext is a strict versioned object containing only `schemaVersion`,
a `catalogId` from `protocol/device-icons-v1.json`, and `accentHue`. Its AES-GCM key uses HKDF info
`candy-sync/v1/device-icon/{deviceKeyFingerprint}`. AAD is the exact JSON encoding
of `["candy-sync-device-icon", 1, workspaceId, deviceKeyFingerprint]`. Clients
derive that fingerprint from the returned DER SPKI before decryption. Cross-device
envelope substitution therefore fails authentication. Choice and visual seed are
stable for one P-256 device identity. Unknown fields and out-of-range values fail
closed after decryption. The server sees only envelope size.

Device-name encryption uses the same record-identity boundary with distinct HKDF
info `candy-sync/v1/device-name/{deviceKeyFingerprint}` and AAD JSON encoding
`["candy-sync-device-name", 1, workspaceId, deviceKeyFingerprint]`. Name and icon
envelopes therefore cannot be swapped across device records or between domains.

## Storage

| Area | Contents |
| --- | --- |
| `storage.local` | Endpoint, username, device label, selection, IDs, sync cursor, encrypted vault, durable encrypted outbox, stable browser-tab UUID map, redacted status |
| `storage.session` | Decrypted vault secrets for the current browser session |
| `storage.sync` | Never used |

`storage.local` is not itself encrypted; all secrets stored there remain inside
the AES-GCM vault envelope. Vault plaintext contains the workspace key, device
private-key PKCS8 bytes, device token, workspace ID, and device ID.

`storage.session` is memory-backed, restricted to `TRUSTED_CONTEXTS` where the
browser exposes that setting, and cleared when the browser exits. It survives a
service-worker suspension but not a browser restart. The extension contains no
content scripts and no web-accessible resources.

## Local security boundary

WebExtensions have no portable operating-system keychain or Secure Enclave
access. Protection covers the server, network ciphertext, backups, and a locked
browser profile. An unlocked profile, local malware, a browser debugger, or a
compromised extension update can access plaintext.

JavaScript also cannot guarantee complete memory erasure. The extension clears
password and passphrase fields early and overwrites temporary byte arrays where
possible, but does not promise hardware-backed zeroization. Input fields use
`autocomplete="off"`; browsers or installed password managers may ignore this
hint. Users must therefore also protect the E2EE passphrase from such local
components.

## Test gates

- RFC 9106 Argon2id known-answer test.
- Vault round trip and wrong-passphrase rejection.
- Recovery-envelope round trip, strict KDF validation, and second-device recovery.
- P-256 key generation, canonical SPKI/PKCS8 import, server fingerprint validation,
  and cross-device icon-envelope substitution rejection.
- AES-GCM round trip, ciphertext bit flip, and AAD manipulation.
- Property-based Unicode and JSON round trips.
- KDF downgrade and denial-of-service boundaries.
- Endpoint and permission rules.
- Exclusion of private and internal tabs.
- API tests proving that the passphrase is never sent and the password appears
  only in the Basic Authorization header.
- Durable outbox, idempotent retry, and revision-conflict behavior.
- Browser-specific manifest and background tests.
- Real extension-crypto integration against the Compose test server.
- `npm audit`: no known production or development vulnerability.

## Remaining before a full multi-device release

- Direct authorized device pairing without sharing the passphrase.
- Signed device membership and cryptographic key rotation after revocation.
- Shared Go/TypeScript cryptographic known-answer vectors for the final wire
  schema; current shared protocol fixtures validate structure only.
- Automated end-to-end tests in real Chromium and Firefox profiles against the
  Compose test server.
