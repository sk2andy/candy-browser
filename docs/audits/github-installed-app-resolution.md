# Installed GitHub app resolution

| Reproduction | Value |
| --- | --- |
| Date / platform | 2026-10-21; dedicated Android API 35 emulator |
| Real app | GitHub `com.github.android`, version 1.281.0 (952), signed in manually |
| Authenticity | APK signing SHA-256 `df08c9f2d809189d9d506497c15745a7395a41536efb433e3aee1aedbe11b261`, matching [GitHub's published association](https://github.com/.well-known/assetlinks.json) |
| Initial state | GitHub's HTTPS aliases are disabled before its account setup; the signed-in app enabled `DeepLinkAliasActivityApi35` |

## Findings

| Boundary | Observed failure | Correction |
| --- | --- | --- |
| Package visibility | Shell sees installed, signed-in GitHub; Candy's `getApplicationInfo` throws `NameNotFoundException` with only scheme-based web queries | Declare `MAIN` / `LAUNCHER` visibility so launchable apps remain candidates; actual URL-specific handler validation still decides eligibility |
| Empty URL path | Real Android resolver rejects `https://github.com` but accepts `https://github.com/` | `ExternalAppWebUrlRules` inserts `/` only in Android lookup/start targets, preserving raw query/fragment and the original browser target |
| App-owned routing | GitHub accepts its slash-root link then returns the homepage to Android's browser chooser; a public repository remains in the real GitHub app | Keep this separate from missing-app discovery; successful handoff does not promise the target app will render every page |

The visibility behavior is consistent with Android's component-filter matching for query intents
([Android 15 `AppsFilterUtils`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/services/core/java/com/android/server/pm/AppsFilterUtils.java)).
The real GitHub alias uses URI-relative path groups. A launcher visibility query makes the package
available without allowing generic browsers or catch-all download handlers to receive site links.

## Reproduction checks

| Test | Purpose |
| --- | --- |
| `ExternalAppWebUrlRulesTest` | Root paths, ports, Unicode/IPv6 hosts, raw escaping, empty suffixes and unsafe input |
| `ExternalAppLauncherInstrumentedTest` | Real `/.*` fixture filter, consistent default/scoped/intent targets and original browser fallback |
| `InstalledGitHubAppInstrumentedTest` | Opt-in real package manager, real app starts, real MainActivity/controller and background/resume Snackbar flow |
| Controller regression selection | Typed address in Automatic/Ask, unsupported target fallback and one-shot returned links |

Run the installed-app suite only on a dedicated device with GitHub already installed and signed in:

```sh
ANDROID_SERIAL=emulator-5586 ./gradlew connectedFullDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.sk2andy.materialbrowser.browser.integration.InstalledGitHubAppInstrumentedTest \
  -Pandroid.testInstrumentationRunnerArguments.installedGitHub=true
```

Default CI skips the optional installed-app suite. It still compiles it and runs the deterministic
rules/fixture coverage. The tests restore Candy preferences and history; they do not change GitHub's
account or store credentials, tokens, screenshots of account screens, or the downloaded APK in the repository.


## Verified result

| Check | Result |
| --- | --- |
| Full / Foss JVM suites | 2,092 tests each passed |
| Deterministic Android regressions | 31 passed, including scoped fixture discovery, generic-handler exclusion, typed/Ask/fallback routing and the retained attached-preview inset check |
| Real installed GitHub suite | 3 passed: real root/repository launch, address-bar handoff + lifecycle return + Snackbar actions, explicit repository handoff + return feedback |
| Full / Foss lint and debug APKs | Passed |

The initial real-app tests failed at package visibility before the manifest change. After visibility
was added, the remaining setup failures were UI-test key-injection/animated-semantics races; the
final installed-app run passed all three cases. A duplicate, weaker detached-preview test on the
current main branch was removed to unblock Android test compilation; its attached-preview version
was retained and passed in the focused regression run.
