# SearXNG search

## Contract

| Concern | Rule | Owner |
| --- | --- | --- |
| Name | The user-facing search and suggestion option is `SearXNG`. | `SearchEngine`, `SearchSuggestionProvider` |
| Instance | Users provide an HTTP or HTTPS base URL. An optional deployment path is preserved; credentials, query parameters, and fragments are rejected. HTTPS is recommended. | `SearxngRules` |
| Search | Search input opens `GET {instance}/search?q={encodedQuery}` as an HTML navigation. Candy does not require the instance JSON API because instances may disable it. | `SearxngRules`, `AddressResolver` |
| Suggestions | The optional provider calls `GET {instance}/autocompleter?q={encodedQuery}` after four characters and accepts OpenSearch or flat JSON arrays. The instance must enable autocomplete and expose the endpoint without a separate browser-login challenge. | `SearchSuggestionRules`, `SearchSuggestionClient` |
| Fallback | Optional suggestion fallback is disabled by default. It runs only after a failed SearXNG request, never after a valid empty response, and may send the typed query to the selected second provider. | `SearxngSettings`, `SearchSuggestionClient` |
| Persistence | Instance and fallback are global browser preferences. Inputs are trimmed, bounded, and recursive SearXNG fallback is removed at the store boundary. | `BrowserSessionStore` |
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
| Compose | Instance field and fallback provider update the hoisted settings model |

Official protocol references: [SearXNG search API](https://docs.searxng.org/dev/search_api.html) and [autocomplete route](https://github.com/searxng/searxng/blob/master/searx/webapp.py).
