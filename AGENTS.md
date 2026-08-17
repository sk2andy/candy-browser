# Candy Browser agent guide

## Start here

| Work | Read first |
| --- | --- |
| Any code change | [`docs/README.md`](docs/README.md), then the matching feature `README.md` |
| Kotlin, Compose, tests, scripts | [`.agents/skills/coding-style/SKILL.md`](.agents/skills/coding-style/SKILL.md) and only the relevant references |
| Filter assets or site defaults | [`docs/privacy-protection/README.md`](docs/privacy-protection/README.md) and the matching generator script |
| Release or signing | Root [`README.md`](README.md#signed-release-builds) and [`.github/workflows/release.yml`](.github/workflows/release.yml) |

## Project map

| Area | Responsibility |
| --- | --- |
| `MainActivity.kt` | Android lifecycle, activity-result contracts, intents, root composition |
| `browser/BrowserController.kt` | Browser/WebView orchestration and observable app state |
| `ui/` | Compose screens, motion, layout, presentation rules |
| `browser/` | Browser models, WebView policies, commands, permissions, integrations |
| `data/` | Stores, repositories, persistence and deterministic data rules |
| `blocking/` | Network/cosmetic filtering, Candy Rules, Privacy X-Ray |
| `capsule/` | Site Capsule model, navigation, shortcuts and editor contracts |
| `reader/` | Reader extraction, library, session and speech |

## Working rules

- Keep `MainActivity`, `BrowserController`, and `BrowserScreen` focused on wiring and orchestration. Put new deterministic decisions in focused models or `*Rules` objects.
- Keep Android/WebView effects at package edges; unit-test policy without Android when possible.
- Preserve private-mode boundaries: no private tabs, trails, rules, permissions, or reader state may enter persistent storage. Never issue remote search-suggestion requests for private input.
- Reuse `BrowserUriPolicy`, permission origin rules, and existing filter validators for untrusted input.
- Compare neighboring production code and mirrored tests before editing. Follow `$coding-style`.
- Update feature docs when behavior, invariants, storage, or ownership changes. Keep feature `README.md` files as lookup tables.

## Verification

- Every agent session must use its own dedicated emulator for Android tests. Never share an
  emulator or physical device between concurrent agent sessions. Set `ANDROID_SERIAL` for Gradle
  device tests and pass the same explicit serial with `adb -s` for every ADB command.

| Change | Minimum check |
| --- | --- |
| Pure Kotlin rules/models | `./gradlew testDebugUnitTest` |
| Android resources, manifest, build config | `./gradlew lintDebug assembleDebug` |
| Compose, WebView, storage, Android contracts | Relevant `src/androidTest` test on an API 34+ device/emulator |
| Filter compiler or generated assets | Matching script tests plus clean generated-asset diff |
| Release path | Follow release workflow checks; never expose or commit signing material |
