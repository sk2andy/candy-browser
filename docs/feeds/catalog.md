# International feed catalog

## Product behavior

| Surface | Contract |
| --- | --- |
| Source | Bundled Plenary `awesome-rss-feeds`, pinned to `9ab2482cae9031a5a39635c887fcfc8ece7e130a`; 837 supplied addresses, 832 after canonical URL deduplication, all supplied countries and topics. No RSS Amplifier calls and no additional sources |
| Search | Native filled M3 SearchBar accepts terms and feed/website addresses. Terms match local title, publisher/feed host, category and description; all tokens must match and exact publisher/title matches rank first. Valid HTTP(S) or scheme-free domain input is identified locally while typing; only explicit submit opens the original preview/discovery sheet. Invalid URLs do not trigger requests. There is no separate feed-address button in the catalog |
| Coverage | A curated starting set, not the entire web. Golem is absent in this pinned list; an empty result explains address entry in the search field. New catalogs can be added later through the focused catalog adapter |
| Metadata | Plenary does not reliably specify language or blog/news type. Do not infer language from country or show filters that cannot work. Preserve international text and source descriptions |
| Topics | Six topic groups in a photo mosaic: one tall lead image beside two stacked details, a full-width panorama and two unequal bottom cards. Photos fill asymmetric rounded silhouettes; white titles overlay a dark gradient scrim. Pressed corners use theme spatial springs, respecting reduced motion. All six topics fit a regular phone; large text uses readable single-column cards with scrolling |
| Tablet layout | Discovery heading, mosaic and footer share the search bar’s centered 720dp outer width and 16dp side padding. Tablet cards are taller to preserve photo proportions; search results retain the wider adaptive grid. These dimensions are Candy design choices |
| Images | One bundled six-scene atlas, drawn by source region. No remote image requests. Labels/colors follow current Candy theme; mosaic proportions and accessible single-column layouts adapt to width and text scale |
| Preview | Original publisher feed preview; network only on preview/open/refresh. Subscription remains explicit and supports folders; manual addresses always available |
| Restoration | In normal profiles, submitted query/topic and typed draft survive recreation; private drafts stay in memory only. Restored explicit searches read the local catalog again. Source payloads stay out of the saved-state Bundle |
| Pagination | Deterministic local pages of 40, canonical URL deduplication. Never fabricate a provider result or a global search total |
| Topic navigation | A selected photo tile expands into the results screen with the theme spatial spring, retaining its asymmetric corners during expansion. Discovery stays composed beneath the results. Reduced motion and restored searches open directly |
| Back | Results use native predictive back to reveal discovery; cancellation retains the query and results. Catalog back returns to the invoking collection or RSS settings. Sheet back remains owned by Material |
| Access | Normal profile ownership and transfer/lock checks run before/after lookup. Private preview and address drafts remain memory-only; private search terms never call the catalog. No query history is saved |

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
| Local search | `FeedCuratedCatalog`, `FeedCatalogRules`, `FeedCatalogInputRules`, `FeedCatalogModels` |
| Profile access | `FeedRepository#searchCatalog` |
| UI | `FeedCatalogScreen`, `FeedCatalogComponents`, `FeedCatalogTopicCard`, `FeedCatalogResultsTransition`, `FeedCatalogMorphRules` |
| Original preview | `FeedSubscribeSheet` |
| Older adapter | `FeedCatalogClient` and `FeedCatalogNetwork` retained but no production caller; API tests document its old contract |
| Tests | `FeedCuratedCatalogTest`, `FeedCatalogInputRulesTest`, `FeedCatalogScreenInstrumentedTest`, `FeedCatalogNavigationInstrumentedTest` |
| Approved style exploration | `docs/design-concepts/rss/themen-entwuerfe.html` (Formenspiel selected) |
