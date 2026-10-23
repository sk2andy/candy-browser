# International feed catalog

## Product behavior

| Surface | Contract |
| --- | --- |
| Source | Bundled Plenary `awesome-rss-feeds`, pinned to `9ab2482cae9031a5a39635c887fcfc8ece7e130a`; 837 supplied addresses, 832 after canonical URL deduplication, all supplied countries and topics. No RSS Amplifier calls and no additional sources |
| Search | Native filled M3 SearchBar. Local title, publisher/feed host, category and description matching; all query tokens must match. Exact publisher/title matches rank first. Search never connects to a server |
| Coverage | A curated starting set, not the entire web. Golem is absent in this pinned list; an empty result offers manual feed-address entry. New catalogs can be added later through the focused catalog adapter |
| Metadata | Plenary does not reliably specify language or blog/news type. Do not infer language from country or show filters that cannot work. Preserve international text and source descriptions |
| Topics | Six topic groups, horizontal single-row filters. Image masks use MaterialShapes Arch, Gem, Slanted, Cookie4Sided, Fan and Triangle; pressed images morph toward Square with theme spatial springs. Stable hit areas and unmasked text. Compact square photos keep all six topics visible on a regular phone; large text can scroll for accessibility |
| Images | One bundled six-scene atlas, drawn by source region. No remote image requests. Labels/colors follow current Candy theme; one/two/three-column layouts adapt to width and text scale |
| Preview | Original publisher feed preview; network only on preview/open/refresh. Subscription remains explicit and supports folders; manual addresses always available |
| Restoration | Submitted query/topic and typed draft survive recreation; restored explicit searches read the local catalog again. Source payloads stay out of the saved-state Bundle |
| Pagination | Deterministic local pages of 40, canonical URL deduplication. Never fabricate a provider result or a global search total |
| Back | Catalog retains the collection underneath its spring transition. Predictive back reveals that parent; cancellation keeps catalog state. Sheet back remains owned by Material |
| Access | Normal profile ownership and transfer/lock checks run before/after lookup. Private preview remains memory-only. No query history is saved |

## Data maintenance

| Item | Location / rule |
| --- | --- |
| Upstream | [Plenary awesome-rss-feeds](https://github.com/plenaryapp/awesome-rss-feeds/tree/9ab2482cae9031a5a39635c887fcfc8ece7e130a), CC0-1.0 |
| Generator | `scripts/generate_feed_catalog.py`; pinned archive, bounded file/source counts, deterministic sorting, URL validation, exact documented repair of upstream malformed XML |
| Runtime asset | `app/src/main/assets/feed_catalog/plenary.json`; shipped license beside asset |
| Refresh | Deliberately update pinned revision, inspect category/schema changes, run generator and catalog regressions; never silently update at app startup |
| User additions | Manual subscriptions use existing repository; no changes to bundled list or downloaded search index |

## Ownership

| Area | Files |
| --- | --- |
| Local search | `FeedCuratedCatalog`, `FeedCatalogRules`, `FeedCatalogModels` |
| Profile access | `FeedRepository#searchCatalog` |
| UI | `FeedCatalogScreen`, `FeedCatalogComponents`, `FeedCatalogTopicCard` |
| Original preview | `FeedSubscribeSheet` |
| Older adapter | `FeedCatalogClient` and `FeedCatalogNetwork` retained but no production caller; API tests document its old contract |
| Tests | `FeedCuratedCatalogTest`, `FeedCatalogScreenInstrumentedTest`, `FeedCatalogNavigationInstrumentedTest` |
| Approved style exploration | `docs/design-concepts/rss/themen-entwuerfe.html` (Formenspiel selected) |
