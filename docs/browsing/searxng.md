# SearXNG search

## Contract

### User search shortcuts

| Concern | Rule | Owner |
| --- | --- | --- |
| Definition | Search settings store up to 30 user-defined keyword and URL-template pairs. The dedicated section supports creating, editing and removing shortcuts; GitHub, YouTube and Amazon examples prefill an editable draft. A template must be valid HTTP(S) and contain exactly one `%s`; aliases accept an optional leading `!`, are case-insensitive, unique, and cannot use built-in engine IDs. | `SearchShortcutRules`, `SearchSettingsPage` |
| Address input | `!keyword ` commits a registered shortcut as an animated leading chip; the space permits overlapping aliases. Only the remaining query is visible in the editor. Unknown or unfinished aliases remain ordinary text. Legacy `keyword search terms` remains supported. Search terms are UTF-8 percent-encoded before replacing `%s`. | `AddressResolver`, `SearchShortcutRules`, `AddressSearchShortcutRules`, `AddressSearchShortcutChip` |
| Editing | Backspace deletes query text first, then removes the chip on the next press when the query is empty, for software and hardware keyboards. Tapping the chip removes its search scope while retaining query text. Submitting an empty chip does nothing. | `AddressBarFieldContent`, `AddressBarPrefixVisualTransformation`, `AddressSearchShortcutRules`, `BrowserScreen` |
| Autocomplete | The configured suggestion provider receives only query text under the usual length, debounce and private-mode rules. Selecting or filling a suggestion retains the shortcut. Scoped queries omit unrelated navigation, Recall and browser-command suggestions, domain ghost completion and the AI-mode toggle. No per-site suggestion endpoint is contacted. | `BrowserScreen`, `BrowserController.addressSuggestionItems`, `SearchSuggestionRules` |
| Persistence | Sanitized shortcuts are saved in regular browser preferences. Private input is used only for the current navigation; no query or tab state is added to persistence. | `BrowserSessionStore` |

| Concern | Rule | Owner |
| --- | --- | --- |
| Name | The user-facing search and suggestion option is `SearXNG`. | `SearchEngine`, `SearchSuggestionProvider` |
| Instance | Users provide an HTTP or HTTPS base URL. An optional deployment path is preserved; credentials, query parameters, and fragments are rejected. HTTPS is recommended. | `SearxngRules` |
| Search | Search input opens `GET {instance}/search?q={encodedQuery}` as an HTML navigation. Candy does not require the instance JSON API because instances may disable it. | `SearxngRules`, `AddressResolver` |
| Suggestions | The optional provider calls `GET {instance}/autocompleter?q={encodedQuery}` after four characters and accepts OpenSearch or flat JSON arrays. The instance must enable autocomplete and expose the endpoint without a separate browser-login challenge. | `SearchSuggestionRules`, `SearchSuggestionClient` |
| Fallback | Optional suggestion fallback is disabled by default. It runs only after a failed SearXNG request, never after a valid empty response, and may send the typed query to the selected second provider. | `SearxngSettings`, `SearchSuggestionClient` |
| Fallback settings | The entire fallback control and explanation appear only when SearXNG is the suggestion provider, independently of the search engine. Other providers, including disabled suggestions, omit the section. Switching providers preserves the saved fallback choice. | `SearchSettingsPage` |
| Persistence | Instance and fallback are global browser preferences. Inputs are trimmed and bounded at the shared rule boundary; recursive SearXNG fallback is removed at the Android store boundary. iOS persists only the shared stable provider ID and normalized instance URL. | `SearchSettingsRules`, `BrowserSessionStore`, iOS preference adapter |
| Private tabs | Remote suggestions and their fallback never run. Search navigation still uses the selected SearXNG instance, while private tab state remains memory-only. | `SearchSuggestionRules`, `BrowserController` |

## Failure behavior

- Invalid or incomplete instance input cannot build a SearXNG endpoint. Address submission stops with a configuration hint and never sends the query to another provider.
- Suggestion HTTP failures, timeouts, oversized bodies, and malformed JSON return no suggestions unless the user explicitly selected a fallback provider.
- A successful empty suggestion response stays empty. It does not leak the query to the fallback.
- TLS errors are handled by Android's existing WebView and network trust policy; SearXNG configuration never bypasses certificate validation.

## Verification

| Layer | Coverage |
| --- | --- |
| JVM | Instance validation, path preservation, encoding, search resolution, provider request rules, OpenSearch and flat response parsing |
| Android store | SearXNG settings round trip, length bound, corrupt or recursive fallback recovery |
| Compose | Instance field and fallback provider update the hoisted settings model; fallback section is absent for all other suggestion providers and restores its saved choice after switching back |

Official protocol references: [SearXNG search API](https://docs.searxng.org/dev/search_api.html) and [autocomplete route](https://github.com/searxng/searxng/blob/master/searx/webapp.py).
