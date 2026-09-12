# Candy Sync WebExtension

Manifest V3 vertical slice for Chromium and Firefox. The extension has no
toolbar popup. Its full-page Options Page configures a self-hosted server,
local E2EE keys, and the desired sync scopes.

## Included

| Area | Status |
| --- | --- |
| Endpoint, username, one-time password, and device name | Implemented |
| Immutable, strictly local E2EE passphrase | Implemented |
| CSPRNG workspace key and ECDH P-256 device identity | Implemented |
| Encrypted stable device icon descriptor | Implemented |
| User-selectable shared Android/WebExtension profile-icon catalog | Implemented |
| Argon2id-protected local vault | Implemented |
| AES-256-GCM-encrypted tab snapshot | Implemented |
| AES-256-GCM-encrypted protocol-v2 tab mutations | Implemented |
| Durable delta outbox, REST catch-up, and WebSocket notifications | Implemented |
| Apply Android edits to the matching desktop synced profile | Implemented |
| Chromium service worker | Implemented |
| Firefox non-persistent event page | Implemented |
| Optional tabs and tab-groups permissions | Implemented |
| Bookmark and group merge | Not yet part of this vertical slice |
| Additional device through passphrase recovery | Implemented |
| Direct device pairing without a passphrase | Not yet part of this vertical slice |

Group assignments for existing tabs are already included in the encrypted tab
snapshot. Dedicated bookmark sync will follow with UUID mapping, an apply
journal, and tombstones. Its disabled Options Page row reserves the product
direction, but neither browser build declares bookmark access before the feature exists.

## Local development

Prerequisite: Node.js 20.19 or newer.

```sh
npm ci
npm run verify
```

Individual checks:

```sh
npm run typecheck
npm test
npm run build:chromium
npm run build:firefox
npm run test:build
npm run lint:manifests
npm run verify:reproducible
```

Builds are written to `dist/chromium/` and `dist/firefox/`. `package-lock.json`
and all direct dependencies are pinned to exact versions. The build downloads
no code or assets from the network.

## Load the extension

Chromium:

1. Run `npm run build:chromium`.
2. Open `chrome://extensions` and enable Developer mode.
3. Select **Load unpacked** and choose `dist/chromium`.
4. Open **Options** from the extension management page.

Firefox:

1. Run `npm run build:firefox`.
2. Open `about:debugging#/runtime/this-firefox`.
3. Load the temporary add-on from `dist/firefox/manifest.json`.
4. Open the extension settings from the add-on management page.

## API v1 in this vertical slice

| Request | Authentication | Purpose |
| --- | --- | --- |
| `GET /.well-known/candy-sync` | None | Protocol v1 discovery and limits |
| `GET /v1/bootstrap` | Basic | Workspace bootstrap |
| `POST /v1/devices` | Basic | Register a device and issue its device token |
| `POST /v1/sync/push` | Bearer | Push an encrypted tab snapshot |
| `GET /v1/sync/pull` | Bearer | Pull writer-audited encrypted changes before local push |
| `PUT /v1/devices/{targetId}/tabs` | Bearer | CAS-update an active target synced profile |

`POST /v1/devices` receives only the encrypted device name, encrypted icon descriptor,
P-256 public key and fingerprint, and capabilities. The server password exists only
during setup in the Options Page context. The E2EE passphrase is never part of a request.

HTTPS is required for remote endpoints in the browser packages. Localhost HTTP remains available
for same-device development. The protocol client still validates the server's `allowHttp` discovery
flag before any authenticated remote-HTTP request, but the store manifests do not grant remote HTTP
host access and the Options Page rejects that configuration.

## Protocol v2 realtime path

When discovery advertises protocol version 2 plus `tab-mutations-v2` and `realtime`, local tab
events create individual encrypted `open`, `navigate`, `close`, `reorder`, or `set-pinned`
mutations. The durable outbox preserves the exact change ID, mutation ID, nonce, and ciphertext
used for idempotent retry. Consecutive navigation updates coalesce; a close supersedes pending
updates for the same tab when their revision chain is contiguous.

`POST /v2/sync/push` commits one encrypted mutation. `GET /v2/sync/pull` recovers missed changes
from a v2-specific cursor. A short-lived single-use ticket from `POST /v2/realtime/tickets`
authenticates `WSS /v2/realtime`; committed frames are applied directly when their target revision
is contiguous. Gaps trigger REST catch-up. Chromium receives a 20-second application heartbeat;
Firefox uses the same connection best-effort. A one-minute alarm remains the background fallback.

Disabling tab sync closes realtime delivery and stops both applying and queueing tab mutations.
Re-enabling it compares the current stable tab IDs with the ID-only disable boundary, then sends
encrypted opens, closes, navigations, pin changes, and final order. URLs and titles are not retained
as plaintext for this reconciliation. REST pagination and push acknowledgements keep cursors
monotonic and fail closed on cursor cycles.

Protocol-v1 cursor, revision, snapshot outbox, and endpoint path remain separate and are used when
the server does not advertise the complete v2 feature set.

## Permission lifecycle

- `storage` and `alarms` are baseline permissions.
- `tabs` and `tabGroups` are requested only after selection.
- Bookmark access is not declared while bookmark synchronization remains unavailable.
- Endpoint access is requested through direct user action as the exact origin
  subset of the optional host patterns.
- `permissions.contains`, `onAdded`, and `onRemoved` determine effective state.
- Private tabs and non-HTTP(S) URLs are always discarded.
- Remote reconciliation updates URL, pin and order; creates missing tabs; and closes
  absent eligible tabs. It never touches private tabs, internal pages, or local files.
- A durable local UUID map keeps Candy tab identity stable across navigation and is
  pruned when eligible tabs close.
- Browser tab IDs reported missing during remote mutation application are pruned, so pull recovery
  and queued uploads continue; unrelated browser API failures still stop the pass.
- Loading transitions retain that UUID and prefer a syncable `pendingUrl`; only a
  committed excluded destination emits `close`.

Entering an HTTP endpoint shows an inline transport warning. Localhost HTTP remains available for
same-device development; remote HTTP is blocked because it cannot protect enrollment credentials
or device tokens in transit.

The public project website lives at <https://sk2andy.github.io/candy-browser/> and its English
privacy policy at <https://sk2andy.github.io/candy-browser/privacy/>.

## Status

The background process stores only redacted status in `storage.local`. A browser
restart clears `storage.session`; the extension then reports **Locked** and asks
for the passphrase again. A periodic alarm is a safety net, not a requirement
for continuous execution.

See [SECURITY.md](SECURITY.md) for algorithms, storage invariants, and limits.
