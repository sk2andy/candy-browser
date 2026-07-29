# Candy Browser

A small Android browser with Arc-inspired interactions and a Material 3 Expressive design. The app
uses Android System WebView as its Chromium engine and adds its own tabs, floating browser chrome,
and local content protection.

## Features

- Edge-to-edge web content with a floating bottom toolbar
- Material 3 dynamic color and OS-controlled dark mode
- Multiple tabs with an Arc-inspired card overview, persistently stored previews, favicon/title, hero animation, and persistent sessions
- Candy Trails: branching, persistent navigation journeys for each regular tab, with an animated graph, pan/zoom, and direct node navigation
- Tab switching by horizontally dragging the address bar; swiping up opens the overview
- Tabs can be swiped upward and closed with a rubber-band effect
- Haptic feedback when switching, opening, and closing tabs and when creating new tabs
- Flicker-free tab handoff and bidirectional hero animation
- Pull to refresh and Material 3 loading progress directly in the address bar
- Direct URL navigation, with Google search as the fallback
- Directly executable, localized browser commands in address search via `>`
- Local history with autocomplete and persistent favorites on new tabs
- Local ad/tracker filtering with about 55,000 compiled EasyList/EasyPrivacy hosts, including service worker requests
- An additional pinned, network-only subset derived from the official uBlock Origin Ads source, bundled and active by default for every profile
- Interactive Privacy X-Ray for each tab, with batched live counters, deterministic categories,
  a bounded domain overview, and temporary or profile-specific site exceptions
- Filter Studio for global or profile-specific host, site-to-host, and origin-scoped CSS rules;
  Privacy X-Ray can create concrete block/allow rules directly and open the rule responsible for a match
- Third-party cookies blocked by default; first-party cookies allowed for sign-ins
- Early cookie-banner blocking based on EasyList Cookie List
- Safe Browsing, TLS failure handling, blocked file/content schemes, and an external-scheme allowlist
- Downloads with a system notification to the public Downloads directory
- Long-press menu for image downloads and links in background tabs; native text selection remains available
- User-initiated pop-ups and new windows opened as tabs
- HTTP/HTTPS intent filters, default-browser role, and opening links in external apps
- System Autofill for password managers and WebAuthn/passkey support provided by the installed WebView provider
- Localized About & legal settings with developer/GitHub information, runtime license notices, offline
  Apache terms, and the exact pinned uAssets source and GPL-3.0 license

## Build

Requirements: Android SDK 35 and JDK 17. The app supports Android 14 (API 34) and later.

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew \
  testDebugUnitTest lintDebug assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Limitations

System WebView does not expose Chromium's extension API. Content blocking therefore runs locally
and uses limited heuristics; WebSockets, CNAME cloaking, some redirects, and banners inside closed
cross-origin or Shadow DOM contexts may get through. Blocking every cookie is intentionally not
the default because doing so breaks sign-ins and many websites.

Privacy X-Ray shows locally blocked WebView requests that can be reliably attributed to a tab.
Global service worker requests are excluded from per-tab telemetry because WebView does not provide
a reliable tab ID for them. Cookie information describes active policies only, not observed cookie
events.

### Candy Rules v1

Filter Studio imports the small, versioned Candy format and a deliberately limited, safely
representable subset of Adblock Plus/uBlock lists: exact `||host^` block/allow rules, positive
`domain=`/`from=` host pairs, HOSTS entries, and origin-scoped standard CSS selectors. JavaScript,
regular expressions, negation, redirects, HTML/response-header filters, and advanced cosmetic
operators are visibly skipped. Filter Studio makes no claim of full Adblock Plus or uBlock
compatibility. Before an import, the user explicitly selects one existing profile or all profiles;
unsupported lines require confirmation.

Candy exports remain on `candy-rules:1`. Each following tab-separated line contains `rule`, an
action (`block`, `allow`, or `css`), a type (`host`, `pair`, or `origin`), and a target; additional
fields store the CSS selector, ID, profile, group, and enabled state. Candy imports are limited to
512 KiB and 8,192 lines, while Adblock imports are limited to 5 MiB and 100,000 lines. Imports with
more than 4,096 total rules or 64 cosmetic rules are rejected atomically instead of being partially
accepted.

A pinned, reproducibly generated network-rule subset of the official uBlock Origin Ads source is
bundled with the app and active by default for every profile. Extraction accepts only Candy's exact
host and first-party-to-third-party host-pair semantics. Unsupported paths, resource options, regular expressions,
redirects, cosmetic syntax, scriptlets, and JavaScript are excluded rather than approximated.

User-managed HTTPS subscriptions are imported only after an explicit fetch. Every update first
shows a diff and requires confirmation; it does not silently replace the bundled snapshot. Candy
imports only the safely representable host/pair subset, visibly skips foreign syntax, CSS,
scriptlets, and JavaScript, and never executes them. Subscriptions do not follow redirects and can
apply globally or to exactly one profile. Incognito rules and imports remain in memory only.

## Filter sources

Ad/tracker hosts and cosmetic cookie-banner rules come from the EasyList authors and are used under
Creative Commons Attribution-ShareAlike 3.0 or later. Their source and license are also documented
in `app/src/main/assets/content_filter.LICENSE.txt`. The embedded rule lists, pinned to fixed source
revisions, can be updated reproducibly with `scripts/update_content_filter_hosts.sh` and
`scripts/update_cookie_banner_css.sh`.

The bundled uAssets-derived network subset is generated reproducibly from a pinned revision of the
official uBlock Origin Ads source and distributed under GPL-3.0. Its upstream revision, source URL,
license, transformation notice, and reproduction script are shipped with the generated asset.
Candy makes no claim of full uBlock Origin compatibility, recommendation, or endorsement by the
uBlock Origin project. Optional remote subscription updates remain user-initiated, previewed, and
confirmed.
