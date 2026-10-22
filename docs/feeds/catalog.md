# International feed catalog

## Product behavior

| Surface | Contract |
| --- | --- |
| Entry | Add sources opens the catalog; Feed address is also directly available in Sources and inside the catalog |
| Search | Native filled Candy M3 SearchBar. Opening, typing and changing only the local language selection send no request. Submitting text or choosing a topic is explicit consent to the provider lookup |
| Scope | Blogs and news sources, all languages by default, independent of the app language. Podcasts and article-search hits are excluded |
| Languages | Native, English and current UI-language names plus language codes are searchable. Multiple selections filter the loaded source selection, not a claimed global provider total |
| Types and topics | Tabs select All / Blogs / News; topics use a single horizontally scrollable chip row. Changing type after a submitted query explicitly reloads that query |
| Preview | A source opens the original publisher feed preview. Subscription remains an explicit action, supports a folder, and uses the existing repository rather than catalog metadata as feed content |
| Feedback | Loading, empty selection, errors, retry, existing subscription and save failure have visible states. No demo sources appear in production |
| Restoration | Save only submitted query/topic, selected type/languages and typed draft. Activity recreation reloads page zero only when a query was previously submitted. Normal manual address drafts are restored without submitting them. Source payloads never enter the saved-state Bundle |
| Pagination | Text search has no reliable offset contract and is a finite selection. Topic requests use verified group-specific offsets. Duplicate-only intermediate pages retain the next valid cursor; UI retention is bounded to 500 sources |

## Provider and trust boundaries

| Boundary | Implementation |
| --- | --- |
| Provider | [RSS Amplifier API](https://rssamplifier.com/llms.txt), [terms](https://rssamplifier.com/terms). Public source discovery, no API key; the UI attributes the provider and explains that submitted queries are shared with it |
| Requests | Fixed HTTPS host `rssamplifier.com`, port 443; `/api/search`, group-filtered `/api/topics/{slug}` and `/api/feeds/{slug}`. No browser cookies, authentication or redirects |
| Limits | Query 200 characters, validated topic/slug, 2 MiB response limit, finite connect/read/call timeouts, at most four concurrent metadata requests; topic offset and retained sources bounded |
| Source metadata | URLs use the existing HTTP(S) policy, reject credentials, and canonicalize international hosts. Provider HTML becomes inert text. Feed identity deduplicates canonical original feed URLs |
| Cancellation | A new query cancels the old request; coroutine cancellation cancels its HTTP call. Stale responses cannot replace current results. Closing the catalog cancels its UI work |
| Private and locks | Private browsing cannot use the remote catalog. Access is checked before and after each provider request; locked, deleted or transferring profiles cannot publish results. Manual preview retains the existing private memory-only contract |
| Storage | No catalog search history, cached provider index or automatic background lookup. Catalog failures stay local to the catalog and do not replace the inbox error state |

## Ownership

| Area | Files |
| --- | --- |
| DTOs and validation | `feeds/FeedCatalogModels.kt`, `FeedCatalogRules.kt` |
| Bounded source-only API adapter | `feeds/FeedCatalogClient.kt`, `FeedCatalogNetwork.kt` |
| Profile access | `feeds/FeedRepository.kt#searchCatalog` |
| Catalog and language UI | `ui/feeds/FeedCatalogScreen.kt`, `FeedCatalogComponents.kt`, `FeedCatalogLanguages.kt` |
| Original feed preview and save | `ui/feeds/FeedSubscribeSheet.kt` |
| Draft only | `docs/design-concepts/rss/katalog.js`, `katalog.css`, `preview_server.py`; the localhost preview relay is never used by Android |
