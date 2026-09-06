<p align="center">
  <img src="app/src/main/res/drawable-nodpi/ic_launcher_foreground_art.png" width="120" alt="Candy Browser logo">
</p>

<h1 align="center">Candy Browser</h1>

<p align="center">
  A gesture-first Android browser with Material 3 Expressive design, local privacy tools,
  and a tab system built for visual navigation.
</p>

<p align="center">
  <a href="https://github.com/sk2andy/candy-browser/releases"><img alt="Release" src="https://img.shields.io/github/v/release/sk2andy/candy-browser?display_name=tag&sort=semver"></a>
  <img alt="Android 13+" src="https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-1.9.24-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white">
  <a href="LICENSE"><img alt="License: MPL 2.0" src="https://img.shields.io/badge/License-MPL%202.0-orange.svg"></a>
</p>

<p align="center">
  <a href="https://buymeacoffee.com/sk2andy"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" height="50" alt="Buy me a coffee"></a>
</p>

<p align="center">
  <img src="docs/screenshots/candy-home.png" width="30%" alt="Candy Browser start page">
  &nbsp;
  <img src="docs/screenshots/candy-tabs.png" width="30%" alt="Candy Browser cover-flow tab overview">
  &nbsp;
  <img src="docs/screenshots/candy-privacy.png" width="30%" alt="Candy Browser Privacy X-Ray">
</p>

> [!NOTE]
> I am working on a Gecko-Webview-Implementation that brings Firefox extensions to the app. I have already a working draft but I cannot say for sure that this will work. Also this takes most of my time right now to fix all the broken stuff. So bare with me that I am maybe not that responsive in fixing your bugs or adding your feature requests. Thanks <3 

## Cross-device sync

**Candy Sync turns every connected Android, Chromium, or Firefox device into a writable profile.**
Open a desktop profile on your phone to inspect its live tab list, open new tabs, navigate, pin,
reorder, or close them. The same small tab changes flow back to every connected client instead of
re-uploading the complete browser state.

<p align="center">
  <img src="docs/screenshots/candy-sync-device-profile.png" width="32%" alt="A synced desktop device selected as a writable profile in Candy Browser">
  &nbsp;&nbsp;
  <img src="docs/screenshots/candy-sync-setup.png" width="50%" alt="Candy Sync device binding, profile icon, accent color, and E2EE warning on Android">
</p>

Changes are encrypted locally and written to a durable outbox. REST commits provide ordered,
retry-safe storage; authenticated WebSocket notifications deliver committed changes immediately.
After suspension or network loss, clients recover missing changes through REST.

### E2EE in short

The first client creates a random 256-bit workspace key. The immutable passphrase derives a local
recovery key with Argon2id; HKDF-SHA-256 derives purpose- and device-specific keys, and AES-256-GCM
encrypts and authenticates tab data. Every device also creates its own P-256 private key locally.
The passphrase, workspace key, private keys, URLs, titles, device names, and icons never reach the
server in plaintext—the server stores ciphertext plus the routing metadata required for sync.

### Start Candy Sync

```sh
cd sync/server
cp .env.example .env
# Set a unique username, password, public URL, and TLS host in .env.
# Never put the E2EE passphrase in server configuration.
docker compose up --build -d
```

Build and load the WebExtension from `sync/extension/`, open its browser-managed Options Page, and
enter the endpoint plus an E2EE passphrase. In Candy Browser, open **Settings → Synchronization**
and join with the same endpoint, credentials, and passphrase.

See the [Candy Sync guide](docs/sync/README.md) for server deployment, local TLS, Chromium/Firefox
loading, Android setup, backups, protocol details, and the full security model.

### Candy Sync roadmap

- [ ] Add multi-user support
- [ ] Publish the extension to the Chrome Web Store and Mozilla Add-ons
- [ ] Publish a prebuilt server image to Docker Hub or a similar registry
- [ ] Offer a hosted solution for people who do not want to self-host

## See Candy in motion

https://github.com/user-attachments/assets/cb9b61d4-5047-4b85-b41e-255f9654949f

<p align="center">
  <a href="docs/promo/video/candy-browser-showcase-16x9.mp4"><strong>Download the 16:9 feature showcase (MP4, 60 fps)</strong></a>
</p>

The showcase presents self-hosted Android/Chromium/Firefox tab sync with E2EE directly after the tab
switcher, then combines live API 36 emulator footage, current Topping scripts, and repository screenshots
to show the gesture-first tab switcher, Link Peek, Spoilerfree Sports, Hacker News Comfort, Privacy Toppings,
the current Privacy X-Ray,
Reader Studio, Candy Trails, and profiles. Its original soundtrack, kinetic typography, and camera motion are
generated entirely from repository-owned sources.

## Why Candy?

- **Made for gestures.** Switch tabs from the address bar, swipe into the visual overview, and
  dismiss cards with spring motion and haptic feedback.
- **Private by design.** Filtering, history, favorites, profiles, and privacy telemetry stay local.
- **Cross-device without surrendering your data.** Self-hosted E2EE sync exposes desktop and Android
  tab lists as writable device profiles while the server never sees their contents in plaintext.
- **Feels at home on Android.** Dynamic color, edge-to-edge content, Predictive Back, Autofill,
  passkeys, downloads, sharing, printing, and default-browser integration.

## Tablet support

Candy adapts its fullscreen browsing and visual tab overviews to larger screens, including
landscape-oriented previews in Coverflow and the compact grid layout.

<p align="center">
  <img src="docs/screenshots/candy-tablet.png" width="90%" alt="Candy Browser running fullscreen on an Android tablet in landscape">
</p>

## Features

### Browsing and gestures

- Floating chrome over edge-to-edge WebView content, with configurable actions around the fixed
  address field and a docked edge mode
- Pull to refresh, direct URL navigation, QR scanning, local domain completion, and optional
  provider-backed search suggestions (disabled in private tabs)
- Google, DuckDuckGo, Bing, Brave, Ecosia, Startpage, Qwant, Kagi, Perplexity, ChatGPT, and configurable SearXNG search
- Optional Google AI Mode routing through a toggleable address-bar logo, enabled from search settings
- Address commands with `>` for tab, profile, cache, cookie, and navigation actions
- **Candy Recall:** optional, profile-scoped local full-text search across visited pages from the
  normal address input, History, or `>recall`, with no private-tab or cloud indexing
- Background tabs, sharing, printing, external apps, assistant summaries, and built-in or external
  download managers
- **Link Peek:** long-press a link to inspect it in a live, disposable preview without creating a
  tab or history entry, then send it to a background tab through the pulsing plus target
- **External Link Preview:** optionally open links from other apps in a temporary Candy preview
  instead of creating a tab immediately. A compact bottom pill keeps the current host visible,
  combines the outlined **Open in Candy** action with its profile picker, and offers share, copy,
  find-in-page, and desktop-site actions before the page is promoted to a real tab. Leaving Candy
  discards the temporary preview.

<p align="center">
  <img src="docs/screenshots/candy-link-peek.png" width="32%" alt="Candy Browser Link Peek live preview">
  &nbsp;
  <img src="docs/screenshots/candy-commands.png" width="32%" alt="Candy Browser profile commands in the address bar">
</p>

### Appearance and browser chrome

- System, light, dark, and AMOLED appearances with Material You, Candy, or neutral color palettes
- Clear or frosted browser surfaces plus angular, rounded, or extra-rounded shapes
- Independent transparency controls for general browser chrome and the address bar, with adjustable
  live background blur over loaded pages

<p align="center">
  <img src="docs/screenshots/candy-frosted-address-bar.png" width="42%" alt="Candy Browser frosted transparent address bar over a loaded page">
</p>

### Tabs, profiles, and journeys

- Persistent tabs with saved page previews, favicons, pinning, reordering, and automatic cleanup
- Coverflow, compact grid, and preview-free list layouts
- **Candy Stacks:** group compatible tabs by name and color, choose the stack preview,
  collapse from the card marker in Coverflow or Grid, and choose Coverflow, Grid, or List for the
  folder-style member view
- Tab snoozing with scheduled returns, notifications, and a dedicated snoozed-tab manager
- Optional per-profile WebView storage isolation where the installed provider supports it
- Optional profile controls for a simpler single-profile setup
- Private tabs that keep their session and journey data in memory only
- **Candy Trails:** persistent branching navigation graphs with pan, zoom, direct navigation,
  and forkable paths
- **Site Capsules:** profile-bound home-screen shortcuts with configurable navigation boundaries
  and minimal browser chrome

<p align="center">
  <img src="docs/screenshots/candy-profile-creation.png" width="32%" alt="Creating an isolated Candy Browser profile">
  &nbsp;
  <img src="docs/screenshots/candy-trail.png" width="32%" alt="Candy Trail with a branching navigation journey">
</p>

<p align="center">
  <img src="docs/screenshots/candy-tabs.png" width="30%" alt="Cover-flow tab overview">
  &nbsp;
  <img src="docs/screenshots/candy-tabs-grid.png" width="30%" alt="Compact grid tab overview">
  &nbsp;
  <img src="docs/screenshots/candy-tabs-list.png" width="30%" alt="Preview-free list tab overview">
</p>

### Reading and page tools

- **Reader Studio:** local article extraction with typography, alignment, paper and night themes,
  offline saves, and text-to-speech
- Find in page with live match counts and previous/next navigation that stays visible above the
  keyboard
- **Toppings:** Candy's lightweight, transparent alternative to traditional browser extensions.
  Discover, install, update, toggle, import, and edit bounded userscripts that customize matching
  regular tabs without privileged browser or `GM_*` APIs. The reviewed catalog lives in
  [`candy-browser-toppings`](https://github.com/sk2andy/candy-browser-toppings), so new Toppings do
  not require a Candy Browser release
- Per-domain mute controls for silencing noisy sites
- Optional full immersive mode and a setting that prevents video autoplay

<p align="center">
  <img src="docs/screenshots/candy-reader.png" width="32%" alt="Candy Browser Reader Studio">
</p>

### Media, fullscreen, and picture-in-picture

- Native fullscreen support for HTML5 and YouTube video, including rotation-aware Android system UI
- Automatic Android picture-in-picture when leaving Candy with an active regular-tab video
- Website picture-in-picture buttons for eligible top-level and fullscreen-capable embedded HTML5 video
- Google Cast playback for compatible direct HTML5 MP4, WebM, HLS and DASH video in regular tabs
- Seamless PiP entry and return without pausing or recreating the decoder surface, even when a site
  replaces or restyles its video element
- Draggable in-app mini-player when switching tabs, plus background audio playback where supported
- Android media notification and system controls for play, pause, stop, and seek
- Private-tab media remains transient and never enters PiP, the mini-player, or system media controls

### Local protection

- EasyList/EasyPrivacy hosts and cosmetics, a pinned safely representable uAssets subset, and a
  pinned HaGeZi Pro host delta
- Third-party-cookie blocking and cosmetic cookie-banner hiding
- **Privacy X-Ray:** live per-tab block counts, categories, domains, and exceptions
- **Permission Radar:** per-site camera, microphone, location, and other WebView permission activity
- **Filter Studio:** global or profile rules, import/export, and confirmed HTTPS subscriptions
- Safe Browsing, TLS failure handling, blocked unsafe schemes, and external-scheme allowlisting

## Download

Candy Browser requires Android 13 (API 33) or newer.

[Download Candy Browser](https://github.com/sk2andy/candy-browser/releases)

Standard and User CA production builds check GitHub for updates at startup and offer the signed APK
for download. The FOSS build disables this updater. Android still requires you to open a downloaded
file and approve installation.

### Obtainium

Use the filtered setup link so Obtainium always selects the standard certificate-trust channel:

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium%3A%2F%2Fapp%2F%7B%22id%22%3A%22dev.sk2andy.materialbrowser%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fsk2andy%2Fcandy-browser%22%2C%22author%22%3A%22sk2andy%22%2C%22name%22%3A%22Candy%20Browser%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22apkFilterRegEx%5C%22%3A%5C%22%5ECandyBrowser-v%5B0-9%5D%2B(%3F%3A%5C%5C%5C%5C.%5B0-9%5D%2B)%7B1%2C2%7D-release%5C%5C%5C%5C.apk%24%5C%22%7D%22%7D"><img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" height="48" alt="Get it on Obtainium"></a>

All GitHub APK channels use the same application ID and signing key. Obtainium can track only one
of them at a time. Do not add the unfiltered repository URL: the release contains three installable
APKs, and choosing the wrong asset can silently switch the installed feature set or certificate-trust
policy.

### F-Droid

Candy includes a `foss` distribution flavor for the official F-Droid repository. It removes Google
Play services, Google Cast, Google Code Scanner, and Candy's GitHub update checker. Remote search
suggestions default to off. F-Droid builds this variant from tagged public source and verifies it
against Candy's upstream-signed FOSS APK.

The first prepared F-Droid release is `0.31`. Submission files and the remaining maintainer steps
live in [`distribution/fdroid`](distribution/fdroid/README.md). Candy publishes a signed FOSS
reference APK so F-Droid can verify its source build and preserve update compatibility with the
upstream signing key.

Releases contain three APK channels:

| APK suffix | Certificate trust | Intended use |
| --- | --- | --- |
| `-release.apk` | Android system CAs only | Recommended default |
| `-foss-release.apk` | Android system CAs only | F-Droid reproducible-build reference without proprietary Google integrations |
| `-user-ca-release.apk` | System CAs plus every CA in Android's user store | Explicit opt-in for HTTPS filtering/proxy tools such as AdGuard |

The User CA build has the same application ID and signing key as the other builds, so installing it
replaces another channel without clearing Candy's data. Its launcher label and the warning under
**Settings → Protection & data** identify the broader trust policy. Updates stay on the installed
channel.

**Security warning:** a trusted user CA can inspect and modify all HTTPS traffic made by Candy,
including normal and private tabs, suggestions, filter subscriptions, and update metadata. Only
install the User CA APK when you trust every CA in Android's user credential store and the software
that controls its private key. APK signature verification still protects Candy updates from APKs
signed by another key.

Advanced users who intentionally need this channel can use the
[filtered User CA Obtainium setup](https://apps.obtainium.imranr.dev/redirect?r=obtainium%3A%2F%2Fapp%2F%7B%22id%22%3A%22dev.sk2andy.materialbrowser%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fsk2andy%2Fcandy-browser%22%2C%22author%22%3A%22sk2andy%22%2C%22name%22%3A%22Candy%20Browser%20User%20CA%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22apkFilterRegEx%5C%22%3A%5C%22%5ECandyBrowser-v%5B0-9%5D%2B(%3F%3A%5C%5C%5C%5C.%5B0-9%5D%2B)%7B1%2C2%7D-user-ca-release%5C%5C%5C%5C.apk%24%5C%22%7D%22%7D).

## Build from source

Requirements: Android SDK 35 and JDK 17. Point `JAVA_HOME` to your JDK 17 installation.

```bash
./gradlew testFullDebugUnitTest lintFullDebug assembleFullDebug
```

To verify the F-Droid-compatible build:

```bash
./gradlew testFossDebugUnitTest lintFossDebug assembleFossDebug
```

To build the explicit User CA development variant:

```bash
./gradlew testFullUserCaDebugUnitTest lintFullUserCaDebug assembleFullUserCaDebug
python3 scripts/test_network_security_apks.py
```

Debug APK: `app/build/outputs/apk/full/debug/app-full-debug.apk`. It installs as
`dev.sk2andy.materialbrowser.debug`, uses the label `Candy Browser Debug`, and has a badged launcher
icon, so it can stay installed beside the release app.

### Signed release builds

Release builds are minified with R8 and require a signing key. Never commit a keystore or its
credentials. Configure signing locally with either an ignored project file or environment variables.

For the first release only, create a long-lived key if no release key exists yet. Reuse that same key
for every later release:

```bash
mkdir -p .signing
keytool -genkeypair \
  -keystore .signing/candy-release.keystore \
  -storetype PKCS12 \
  -alias candy \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

For an ignored project file:

```bash
cp keystore.properties.example keystore.properties
# Replace every placeholder in keystore.properties, then:
./gradlew assembleFullLocalRelease
```

Alternatively, export the same values from `~/.zshrc.shared`:

```bash
export CANDY_RELEASE_KEYSTORE_PATH=/absolute/path/to/project/.signing/candy-release.keystore
export CANDY_RELEASE_STORE_PASSWORD='replace-me'
export CANDY_RELEASE_KEY_ALIAS='candy'
export CANDY_RELEASE_KEY_PASSWORD='replace-me'
```

Signed local APK: `app/build/outputs/apk/full/localRelease/app-full-localRelease.apk`

`localRelease` installs beside the GitHub build as `dev.sk2andy.materialbrowser.local` and uses a
separate launcher icon and the label `Candy Browser Local`. GitHub update prompts are disabled for
this side-by-side build because production APKs cannot update its package. The GitHub release
workflow uses `assembleFullRelease`, `assembleFossRelease`, and `assembleFullUserCaRelease`; all
preserve the production application ID and icon. A separate workflow signs and publishes the FOSS
output from explicitly allowlisted release tags.

### GitHub releases

The manual `Release Android APK` workflow tests the selected source revision, builds the FOSS flavor,
builds and verifies the signed standard and User CA APKs, creates a `v<version>` source tag, and
publishes the two signed APKs plus their SHA-256 checksums. Add four repository secrets once:

```bash
base64 < "$CANDY_RELEASE_KEYSTORE_PATH" | gh secret set CANDY_RELEASE_KEYSTORE_BASE64
printf '%s' "$CANDY_RELEASE_STORE_PASSWORD" | gh secret set CANDY_RELEASE_STORE_PASSWORD
printf '%s' "$CANDY_RELEASE_KEY_ALIAS" | gh secret set CANDY_RELEASE_KEY_ALIAS
printf '%s' "$CANDY_RELEASE_KEY_PASSWORD" | gh secret set CANDY_RELEASE_KEY_PASSWORD
```

Pin the public certificate fingerprint separately. This prevents an accidentally replaced keystore
from publishing an APK that installed copies cannot update to:

```bash
keytool -exportcert \
  -keystore "$CANDY_RELEASE_KEYSTORE_PATH" \
  -storepass:env CANDY_RELEASE_STORE_PASSWORD \
  -alias "$CANDY_RELEASE_KEY_ALIAS" \
  | shasum -a 256 \
  | awk '{print $1}' \
  | gh variable set CANDY_RELEASE_CERTIFICATE_SHA256
```

The separate `Publish F-Droid reference APK` workflow builds without access to signing secrets, then
passes the unsigned APK to an isolated signing job that executes no repository code. It accepts only
explicitly allowlisted release tags and commits and uses Android Build Tools 34 for compatibility
with F-Droid's signature-copying process. Reference APKs disable the legacy v1/JAR signature and
enable modern APK signature schemes so F-Droid can copy the signature onto its independent build.
F-Droid accepts the resulting signed APK only when that build matches byte for byte apart from
signing.

The workflow uses the `release` GitHub environment. Configure required reviewers for that environment
if releases should require a manual approval after dispatch.

Before each release, update `candy.versionName` and the monotonically increasing
`candy.versionCode` in `gradle.properties`. The workflow rejects an input version that differs
from the committed version. Add the matching English changelog at
`release-notes/<version>.md`; it is bundled into Android’s one-time What’s New page and published
unchanged as the GitHub Release body. The project skill at
`.agents/skills/release-changelog/SKILL.md` documents the supported Markdown and screenshot format.
Then dispatch a release from GitHub Actions or with GitHub CLI:

```bash
gh workflow run release.yml \
  -f version=0.33 \
  -f changelog=release-notes/0.33.md \
  -f prerelease=false
```

Publish a FOSS reference APK from an allowlisted release tag with:

```bash
gh workflow run publish-fdroid-reference.yml -f tag=v0.33
```

Before each later F-Droid release, add its exact tag and immutable commit to the workflow allowlist
through normal code review, then dispatch the workflow.

Android `versionCode` is stored in source so GitHub and F-Droid can build identical version
metadata from a tag. Tags and releases are created only after full and FOSS tests, release build
checks, APK signing, certificate pinning, and signature verification succeed. Back up the original
keystore and credentials securely: Android updates must always use the same signing key.

## Privacy and limitations

Candy uses Android System WebView as its only browser engine and renderer. It does not bundle a
Chromium fork, extension runtime, proxy, VPN, or second rendering engine. Filtering stays inside
each WebView through local request interception, document-start CSS/DOM rules, and service-worker
interception. WebSockets,
CNAME cloaking, some redirects, and content inside closed cross-origin or Shadow DOM contexts may
get through. Candy pre-registers cosmetic CSS for known GET navigations. Android WebView does not
expose cross-origin form POST targets early enough for origin-scoped document-start registration;
those navigations receive the existing page-commit fallback and can show a brief cosmetic flash.

Privacy X-Ray includes only blocked requests that WebView can reliably attribute to one tab.
Service-worker requests are filtered but excluded from per-tab telemetry when no reliable tab ID
is available.

Google Cast is used only after explicit device selection for compatible regular-tab video. Media
URLs and metadata are sent to the selected Cast device and remain memory-only in Candy. Google's
Cast Sender SDK can send Cast-device interaction activity to Google's logging service; release Data
Safety disclosures must account for the current SDK behavior.

<details>
<summary><strong>Candy Rules and filter-source details</strong></summary>

Candy Rules v1 supports exact host block/allow rules, positive site-to-host pairs, HOSTS entries,
and origin-scoped standard CSS selectors. It deliberately rejects JavaScript, regular expressions,
redirects, response-header filters, scriptlets, and advanced cosmetic operators rather than
approximating them.

Bundled EasyList/EasyPrivacy and uAssets cosmetics are compiled separately from user rules and
merged in memory so exceptions work across sources. Only domain-specific standard `##selector`
rules, their domain exclusions, and matching `#@#` exceptions are retained. Generic selectors,
conditional capability blocks, and procedural operators are skipped. Runtime lookup resolves
selectors for the current host before registering a bounded, exact-origin document-start script;
invalid browser-specific selectors fail independently through `insertRule`. Mail, Maps, and
Accounts Google hosts intentionally receive no bundled cosmetic CSS.

Bundled defaults may additionally use audited, declarative WebView rules: a site-scoped literal
request-path prefix or a known Reject/Remind-later consent control. These rules cannot run imported
JavaScript, never click Accept, and never intercept a main-frame navigation. An exact bundled path
rule may override the general same-party escape only for its audited page-host, request-host, and
literal non-root path prefix.

Imports are bounded and validated atomically. HTTPS subscriptions update only after a user-requested
fetch, show a diff, and require confirmation. Private-profile imports remain in memory.

EasyList, EasyPrivacy, and EasyList Cookie data are distributed under CC BY-SA 3.0 or later. The
uAssets-derived network and cosmetic subsets are generated from one pinned revision of the official
uBlock Origin Ads source and distributed under GPL-3.0. The HaGeZi Pro host delta is generated from
a pinned official revision and distributed under GPL-3.0. Exact sources, revisions,
transformations, and notices ship in `app/src/main/assets/`; maintenance entry points live in
`scripts/update_*.sh`.

</details>

## Languages

English, German, French, Portuguese, and Spanish.

## Licensing

Candy Browser source code is available under the [Mozilla Public License 2.0](LICENSE).
Third-party components and filter data retain their own licenses; bundled notices are available in
`app/src/main/assets/third_party_notices.txt` and inside the app.
