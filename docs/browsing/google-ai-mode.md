# Google AI Mode

## User flow

| Step | Behavior |
| --- | --- |
| Select provider | Choose Google under **Settings → Search**. AI Mode controls are capability-gated and currently appear only for Google. |
| Enable logo | Turn on **Show AI Mode logo**. This preference controls visibility; it does not make every Google query an AI query. |
| Enter query | Type a search query. The sparkle logo appears before the close button. It stays hidden for blank input, URLs, Candy `>` commands, and unsupported providers. |
| Choose mode | Tap the logo to select AI Mode for the current query. Tap it again to return to regular Google Search. |
| Submit | Keyboard Go, hardware Enter, and selected search suggestions use the current mode. Navigation suggestions remain direct URL navigation. |
| Start another edit | AI Mode starts off for every new address-editor session. |

## Routing contract

| Input | Route |
| --- | --- |
| Regular Google query | `https://www.google.com/search?q=<encoded-query>` |
| Google AI Mode query | `https://www.google.com/ai?q=<encoded-query>` |
| HTTP(S) URL | Direct navigation; search mode is ignored |
| Candy command | Command dispatch; search mode is ignored |
| Unsupported search engine with an AI mode value | Safe fallback to that engine's regular search route |

`google.com/ai` is Google's public AI Mode entry. Google currently redirects it to an internal
Search URL that can contain parameters such as `udm=50` and `aep=11`. Those redirect details are
provider-owned and must not become Candy's routing contract. Availability and the returned
experience can still vary by Google account, language, region, and provider rollout. See
[Google Search Help](https://support.google.com/websearch/answer/16011537).

## Ownership

| Concern | Owner |
| --- | --- |
| Provider capability and URL template | [`SearchEngine.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/SearchEngine.kt) |
| URL-versus-search classification | [`AddressResolver.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/AddressResolver.kt) |
| Toggle visibility and guarded mode selection | [`AddressAiModeRules.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/browser/commands/AddressAiModeRules.kt) |
| Global visibility preference and submission wiring | `BrowserSessionStore`, `BrowserController` |
| Editor-session selection, icon semantics, and submission actions | `BrowserScreen`, `AddressAiModeToggle` |
| Settings presentation | `SettingsScreen` |

Only the global `ai_mode_toggle_visible` preference is persisted. Active AI Mode selection is
memory-only editor state and resets when the editor closes, the tab changes, or the input stops
being an eligible search query.

## Privacy and provider boundary

- Candy loads Google's provider page; it does not call or pay for a Candy-operated model API.
- Private tabs keep Candy's normal private-state and suggestion boundaries, but sending a query to
  Google is still a network request to Google. Private mode does not anonymize the provider request.
- Google controls account history, personalization, availability, limits, redirects, and response
  quality. Avoid sensitive prompts and verify important AI answers against their sources.

## Test lookup

| Contract | Tests |
| --- | --- |
| Provider URL, encoding, and unsupported-engine fallback | `SearchEngineTest` |
| Search classification and URL protection | `AddressResolverTest` |
| Capability gating, visibility, and guarded mode selection | `AddressAiModeRulesTest` |
| Persisted visibility preference | `BrowserSessionStoreInstrumentedTest` |
| Toggle semantics and interaction | `AddressAiModeToggleInstrumentedTest` |

