# Candy Browser sync integration

Candy Browser implements Candy Sync on Android and iOS through profile bindings and writable device profiles.
The current device binds to one existing local profile. That profile keeps its own icon and
normal local behavior, gains a sync badge, and is not duplicated in the profile switcher. Every
other active device appears beside local profiles with its encrypted name, freely selected shared
icon, accent, and a local sync badge.

## Mozilla Sync on Android

Android's Sync settings offer separate Candy Sync and Mozilla Sync connections. Mozilla uses
Mozilla Android Components / Application Services, including Firefox Accounts OAuth with PKCE,
the encrypted Tabs engine, and device commands. Gecko builds use Firefox Android's public OAuth
client ID `a2270f727f45f648` with its native return URI
`urn:ietf:wg:oauth:2.0:oob:oauth-redirect-webchannel`, matching Firefox-based Android browsers.
This is the Firefox client registration, not a separate Candy registration; Mozilla's continued
acceptance of this configuration for Candy is not guaranteed. The client ID is public, not a secret.

A bundled WebChannel extension returns account messages directly from Mozilla's HTTPS origin to
the explicit, non-private login session. Candy passes the authorization code and state to the SDK
for the PKCE exchange. No external callback activity or hosted redirect is used. The previously
published GitHub Pages callback is not part of this flow. System WebView-only builds leave Mozilla
Sync unavailable because this login transport requires GeckoView.

Gecko builds use GeckoView Omni and resolve Glean's native capability to Gecko's `libxul.so`.
The Java Glean version must match that Gecko build. System WebView builds keep standalone Glean.

| Behavior | Candy Sync | Mozilla Sync |
| --- | --- | --- |
| Connection | Self-hosted endpoint and Candy credentials | One Mozilla account |
| Current device | Bound to one existing local profile | Bound independently to one existing local profile |
| Other devices | Writable profiles | Read-only tab sheets in the same profile switcher |
| Open remote tab locally | Supported | Supported, as a new tab in the bound profile |
| Remote navigation, pin, reorder, close | Supported | Not part of the native Tabs protocol |
| Send current local tab to another device | Through remote tab mutations | Send Tab, when the target advertises support |
| Private, ephemeral login, internal and file tabs | Excluded | Excluded |
| Provider-specific data | Candy tab mutations | Native tab snapshots; no Zen Spaces or other engines |

Both providers may bind the same local profile. Local changes, including Candy changes applied to
that bound profile, then publish through both connections. Remote profiles are never automatically
bridged, merged by display name, or republished as local tabs. Mozilla's account profile endpoint
describes the account, not separate desktop browsing profiles: the runtime mapping uses each
device's ID. Mozilla IDs are namespaced with `mozilla:` and carry `SyncProvider.Mozilla`, so one
provider's refresh cannot remove or write to the other's devices. Disconnecting either connection
leaves the local profile and the other connection intact.

Selecting a Mozilla device opens a tab sheet without changing the active browsing profile or
creating placeholder browser tabs. Opening a copy uses the usual local profile unlock flow.
OAuth opens in an ephemeral Candy login tab, excluded from session persistence and both providers.
Only the login session receives the account bridge; the SDK verifies OAuth state and PKCE.

Mozilla account secrets use the SDK's Keystore-backed secure account storage. The native tabs cache
is app-private `filesDir/tabs.sqlite`, not Candy's encrypted cache; it and Mozilla preferences/account
state are excluded from Android backup and device transfer. Logout clears the native tabs cache.
Periodic sync uses Mozilla's WorkManager integration. An uninitialized local tabs store does not
upload an empty snapshot after process restart. Candy does not initialize Glean telemetry uploads.

| Owner | Implementation |
| --- | --- |
| SDK account, storage, sync and device commands | `data/sync/MozillaSyncRepository.kt` |
| Provider identity and eligible local tabs | `shared/.../sync/MozillaSyncRules.kt` |
| Profile projection, publishing and local open | `browser/BrowserController.kt` |
| Settings and remote device tabs | `ui/MozillaSyncSettings.kt`, `ui/MozillaDeviceSheet.kt` |
| Native OAuth messages | `data/sync/MozillaAccountWebChannel.kt`, `browser/gecko/GeckoMozillaAccountWebChannel.kt`, `gecko/assets/candy_fxa/` |

Focused unit and Android instrumentation cover provider separation, eligible local tabs, login-tab
exclusion, settings and opening local copies. A real Mozilla login, two-device exchange and network
telemetry observation require test accounts and an actual authenticated device exchange.
The opt-in `GeckoMozillaAccountWebChannelInstrumentedTest` live probe uses
`-e liveMozillaAuth true` to verify a status request/response against Mozilla's actual authorization
page without entering credentials. The other bridge tests run without that flag.

## Current-device profile binding

Setup defaults to the active local profile and lets the user select any existing local profile. The
encrypted Android device identity is then associated locally with that profile ID:

```mermaid
flowchart LR
    P[Existing local profile] <-->|localProfileId / ownDeviceId| B[Sync binding]
    P --> L[BrowserSessionStore, history, Capsules]
    B --> O[Encrypted delta outbox and device projection]
    O <-->|Durable REST + realtime ciphertext| S[Self-hosted server]
```

The binding is local metadata. The server never receives the Candy profile ID. The bound profile
remains a local profile: history, Candy Trails, Site Capsules, WebView isolation, snoozing, and
normal session persistence keep their existing owners. Only its non-private HTTP(S) tab projection
is synchronized. Session-ephemeral federated-login popups and their active opener are excluded from
outbound mutations and protected from inbound reconciliation until the login window closes.

During first binding, Candy assigns stable sync IDs to eligible existing tabs and publishes them.
Remote tabs already present for the device are merged into the same local profile. Private tabs and
untracked blank, internal, local-file, and active federated-login popup tabs are preserved locally.
Once a synchronized tab has been acknowledged, a later remote close removes it locally unless it is
temporarily protected by an active federated-login flow. This distinguishes initial migration from
a real remote deletion without interrupting an authenticator or password-manager round trip.

The binding cannot point at an incognito context, and a profile cannot be deleted while actively
bound. Legacy Android sync settings without a profile ID migrate to the active local profile.

## User model

Selecting a synced profile opens that device's tab snapshot inside the normal Candy tab UI. The
profile is not read-only: a mobile client can open, navigate, pin, reorder, and close its tabs. The desktop
extension applies those changes when the target browser is available.

```mermaid
flowchart LR
    A[Android synced profile] -->|Open / navigate / pin / reorder / close| R[Encrypted mutation queue]
    R -->|CAS encrypted v2 delta| S[Self-hosted server]
    S -->|Committed WebSocket frame or REST pull| E[Chromium or Firefox extension]
    E -->|Apply safe HTTP(S) tab diff| D[Desktop browser tabs]
```

The server records the authenticated writer separately from the target device. An Android device
may therefore update a desktop device's profile without impersonating it.

## Runtime ownership boundary

Synced profiles use a `BrowserProfile`-compatible runtime projection so existing tab and browser
engine interaction remains native. The projection is deliberately excluded from local ownership
stores. Reconciled navigation is dispatched to a resident Gecko session immediately; non-resident
tabs remain lazy and load their reconciled URL when their session is first created.

| Data | Local profile | Synced profile |
| --- | --- | --- |
| Tab/browser-engine interaction while running | Yes | Yes |
| `BrowserSessionStore` tabs and profile | Yes, including stable sync IDs for a bound profile | No |
| Incognito tabs | Yes | Never |
| Candy Trails, snooze, local history, Recall | Yes | Not persisted for synced tabs |
| Site Capsules and isolated WebView storage | Yes | Not owned by synced profiles |
| Encrypted sync cache/outbox | No | Yes |

`BrowserProfile.syncedDeviceId` identifies a runtime projection. `BrowserTab.syncCandyId` is the
stable cross-client tab identity; the Android runtime tab ID remains an implementation detail.

An empty `about:blank` tab is kept locally until its first valid HTTP(S) navigation. It then becomes
an encrypted `Open` mutation. `file:`, browser-internal, malformed, private, and session-ephemeral
federated-login URLs never enter the sync payload.

## Supported mutations

| Mobile action | Durable logical mutation | Target result |
| --- | --- | --- |
| Open a URL | `Open` | Create or adopt the stable `candyId` |
| Navigate, including SPA history updates | `Navigate` | Update URL and bounded title |
| Pin or unpin | `SetPinned` | Update pinned state |
| Drag to reorder | `Reorder` | Reconcile the ordered stable IDs |
| Close | `Close` | Remove the matching desktop tab |

Gecko commit and same-document navigation events are debounced before entering the serialized
repository. Remote hydration is marked until commit so engine callbacks cannot echo the received
URL back as a new mutation. A local HTTP(S) navigation is protected from inbound reconciliation
immediately and remains protected until the same target device, stable tab ID, and normalized URL
appear in repository state. The repository folds pending mutations into its observable state
immediately, persists them in a Keystore-protected cache, and retries them after reconnecting.
Android instrumentation covers this race at both boundaries: controller tests exercise deterministic
event ordering, while the address-bar E2E launches the real activity, submits a search through the
visible editor, loads it in Gecko, injects the stale synchronized URL, and verifies that neither the
selected tab nor the rendered engine returns to the old page.

## Conflicts and delivery

Every target profile has a monotonically increasing revision. With protocol v2, Android encrypts
one logical mutation and sends it as a compare-and-swap delta. On a revision conflict, it pulls the
latest ordered deltas, replays the logical mutation by stable ID, and creates a new attempt. The v1
compatibility path retains encrypted snapshot CAS for servers that do not advertise the complete v2
feature set and whose workspace protocol floor has not been promoted.

A prepared delivery attempt stores its exact change ID, mutation ID, base revision, nonce, and
ciphertext before network I/O. A timeout or process restart reuses those exact bytes. This prevents
a lost response from producing different ciphertext under one idempotency key. A confirmed CAS
conflict retires the attempt before a fresh encrypted attempt is created.

### V2 authenticated encryption

Android, iOS and the WebExtension implement the same v2 derivation and AAD contract:

```text
deltaKey = HKDF-SHA-256(
    workspaceKey,
    salt=UTF8(JSON.stringify([workspaceId, targetDeviceId])),
    info=UTF8("candy-sync/v2/payload/tab-delta"),
    output=32)

aad = JSON.stringify([
    "candy-sync-change", cryptoVersion, keyVersion, schemaVersion,
    workspaceId, writerDeviceId, changeId, mutationId,
    "tabs", targetDeviceId, "delta", baseRevision
])
```

AES-256-GCM uses a fresh 12-byte CSPRNG nonce for every new mutation. Plaintext repeats
`mutationId` and `targetDeviceId`; both must match authenticated metadata after decryption. Workspace,
writer, target, operation, identities, and revision-chain substitution therefore fail authentication.

## Shared device icons

[`device-icons-v1.json`](../../sync/protocol/device-icons-v1.json) is the canonical catalog used by
iOS and Android local-profile pickers, shared sync settings, and both extension builds. Its 54 emoji
icons therefore cannot drift between clients. Users freely select an icon; the encrypted descriptor
stores only:

```json
{
  "schemaVersion": 1,
  "catalogId": "computer",
  "accentHue": 312
}
```

Both mobile builds package the versioned JSON. Unknown catalog IDs fail closed.
The small sync badge is local UI state and is not part of user-controlled encrypted metadata.

## Setup and secrets

The Sync settings page starts with a four-step self-hosting guide. Each step links directly to the
matching server quick start, extension build, first-client setup, or Android setup section before
the connection form. Documentation links open in a new Candy tab so setup context remains available.
Android and iOS settings also offer the public Chrome Web Store and Firefox Add-ons listings.
Each listing has a separate share action that sends only its public installation URL through the
platform share sheet; server endpoints, credentials, and passphrases are never included.

The Sync settings page requests:

- the exact self-hosted endpoint;
- server username and one-time enrollment password;
- the existing local profile represented by this Android device;
- this device's encrypted display name, icon, and palette-picked accent color;
- the E2EE passphrase and confirmation.

The device-accent palette uses a fixed, runtime-compatible row layout. Opening Sync from the real
browser Settings route must render the complete page without depending on experimental Compose
flow-layout ABI.

The server password and E2EE passphrase must differ. Neither input is saved. The passphrase never
leaves the device, is immutable for the workspace, cannot be changed or recovered in either protocol
version, and is needed to enroll future devices. Losing it can make the workspace unrecoverable. Each secret field has an explicit
show/hide control, and the settings page remains scrollable above the on-screen keyboard.

Each platform generates its own P-256 device key locally. Android protects the vault and cache with
Android Keystore; iOS stores vault material in a ThisDeviceOnly Keychain item and protects the atomic
cache with a separate Keychain-backed AES-GCM key. Server responses are parsed with exact keys, bounded
sizes, authenticated metadata, and strict HTTP(S) URL policy.

Remote HTTP endpoints are stored only as provisional setup endpoints. Android performs
unauthenticated discovery and sends no Basic credential or bearer token until the server reports
`allowHttp: true`. That report requires `CANDY_SYNC_ALLOW_HTTP=true` server-side. It is a deliberate
cleartext opt-in, not transport security; HTTPS remains the recommended deployment.

## Foreground and background behavior

The controller starts realtime when Candy Browser enters the foreground and closes the socket when
the activity stops. While foregrounded, a single-use 45-second ticket authenticates the WebSocket;
contiguous committed frames apply immediately. Duplicate cursors are ignored; missing or
non-contiguous cursors trigger ordered REST catch-up from the last durably stored v2 cursor.
Reconnect uses bounded backoff.

The controller also refreshes on foreground start and every 15 seconds while active. Local mutations
push immediately; offline writes remain durable and replay after a later refresh. **Sync now** offers
an explicit recovery action. Android does not keep a permanent background socket and does not wake a
closed app continuously. REST remains durable truth; realtime is a foreground latency accelerator.

On Android, a new offline tab reorder replaces earlier queued reorders for the same device and goes
to the end of the queue. When sync resumes, Candy applies any already queued tab opens and closes
before applying the latest order.

## Protocol floor

Android selects v2 only when discovery advertises version 2, `tab-mutations-v2`, and `realtime`.
The first committed v2 delta atomically raises that workspace's protocol floor. Later v1 tab writes
fail with `409 protocol_upgrade_required`; clients must upgrade rather than fall back. V1 reads stay
available, and v1/v2 cursors remain separate. Before promotion, accepted v1 writes advance the v2
revision baseline to prevent divergent successors.

## Code map

| Path | Responsibility |
| --- | --- |
| `shared/.../sync/CandySyncRepository.kt` | Shared enrollment, pull, CAS, outbox, retry, realtime and observable state |
| `shared/.../sync/SyncProtocolCodec.kt` | Shared strict protocol JSON and authenticated metadata |
| `shared/.../sync/SyncTabRules.kt` | Shared deterministic mutation and URL rules |
| `app/.../sync/SyncCrypto.kt` | Android P-256, HKDF, AES-GCM and Argon2 adapter |
| `data/sync/AndroidSyncStores.kt` | Keystore-backed vault and encrypted cache |
| `iosApp/CandyIos/IosSyncPlatform.swift` | CryptoKit, Keychain cache/vault and dispatch adapters |
| `iosApp/CandyIos/IosSyncTransport.swift` | URLSession REST and WebSocket adapter |
| `browser/SyncedProfileRuntimeRules.kt` | Runtime profile/tab projection and reconciliation |
| `shared/.../ui/settings/SyncSettingsPage.kt` | Shared setup, immutable-passphrase warning, status and device list |

## Verification

```sh
./gradlew testFullDebugUnitTest testFossDebugUnitTest
./gradlew lintFullDebug lintFossDebug assembleFullDebug assembleFossDebug
./sync/scripts/test-all.sh
```

Android security instrumentation requires a dedicated API 34+ emulator with an explicit
`ANDROID_SERIAL`. The repository suite covers known-answer crypto, tampering, wrong passphrases,
strict parsing, Keystore restart behavior, offline retry, lost-response idempotency, and CAS replay.
The MainActivity settings-flow suite also enters Sync through the real browser menu and verifies the
page renders on-device.
Controller instrumentation covers Gecko `Open`/`Navigate`, SPA history changes, first-load hydration
without echo, stale linked-state rejection during local navigation, rapid superseding remote
navigation, and remote navigation of an already resident Gecko session.
`./sync/scripts/test-android.sh` provisions its own disposable API 35 AVD, sets the serial explicitly,
runs the sync unit/UI/security suite, and deletes the AVD afterward.
