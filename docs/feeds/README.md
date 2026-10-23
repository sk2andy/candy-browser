# Feeds and shared library

## Topic lookup

| Need | Detail | Main code |
| --- | --- | --- |
| Browser entry, Reader and return navigation | [Navigation and storage](navigation-and-storage.md) | `FeedsHost`, `FeedsScreen`, `BrowserMainMenu` |
| RSS/Atom, subscriptions, OPML, filters and privacy | [Feed contracts](feed-contracts.md) | `FeedParser`, `FeedRepository`, `FeedRules`, `FeedOpml` |
| Offline copies, speech and opt-in background work | [Navigation and storage](navigation-and-storage.md) | `FeedArticleLoader`, `FeedReaderDataSource`, `FeedPlaybackManager`, `FeedMediaPlaybackService`, `FeedRefreshWorker` |
| Approved interactive design | [Product draft](../design-concepts/rss/umsetzung.md) | `docs/design-concepts/rss/entwurf.html` |
| Android implementation preview | [Library screenshot](../design-concepts/rss/android-bibliothek.png) | Real Compose UI, example test article |
| Article actions and listening player | [Article cards](../design-concepts/rss/android-artikelaktionen.png), [listening queue](../design-concepts/rss/android-vorleseliste.png) | Direct bookmark/queue toggles, real Compose player |

## Test lookup

| Surface | Tests |
| --- | --- |
| XML, dates, URL identity, OPML | `FeedParserTest` |
| Read state, rules, revisions, grouping | `FeedRulesTest`, `FeedSimilarityRulesTest` |
| Profile isolation, locks, private mode, website provenance | `FeedRepositoryInstrumentedTest` |
| Atomic storage and interrupted downloads | `FeedStoreInstrumentedTest` |
| Library and import UI | `FeedsScreenInstrumentedTest` |
| Direct article bookmark and listening queue controls | `FeedArticleRowInstrumentedTest` |
| Player controls, queue and unavailable voice | `FeedQueueContentInstrumentedTest`, `FeedQueueArtworkInstrumentedTest` |
| Background speech, native media controls and access invalidation | `FeedMediaPlaybackServiceInstrumentedTest` |
| Browser menu unread badge | `BrowserMenuBottomSheetInstrumentedTest#feedsUnreadChipTracksCountAndOpensDestination` |

Android owns this feature. Shared menu capabilities default off on other platforms. Newsletter reception, podcasts and device sync remain separate work.
