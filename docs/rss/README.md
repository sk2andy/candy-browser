# RSS feeds

## Behavior

| User action | Result |
| --- | --- |
| Browser menu / RSS settings / Reader home / widget → Discover feeds | Open the shared profile-owned source catalog; private browser tabs do not expose discovery |
| Regular tab switcher → left Reader Studio button | Open Reader Studio directly to Library, without requiring an article; Settings stays on the right and the pinned-tabs jump stays beside the reader action |
| Reader Studio → Feeds tab | Show all cached articles, newest first, in a lazy scrolling list; tabs, source selector and filter chips remain above the list |
| All feeds selector | Open a searchable source sheet; choosing a source filters the list, choosing All feeds restores the combined inbox |
| All posts / Unread chips | Select one filter within the current source; the active chip shows a checkmark |
| Read a feed article successfully | Persist a bounded, feed-scoped read key; returning preserves the inbox position and current unread batch until the source/filter is reapplied |
| Read a saved article | Open the offline snapshot directly with its latest progress; no page navigation or network extraction is required |
| Back from an article opened through the inbox | Return to the same reader overview; a second Back dismisses it to the underlying tab switcher/browser |
| Follow a feed | Fetch and parse it immediately, then save feed metadata and up to 40 article summaries locally |
| Open a followed feed | Show its latest articles in a dedicated, compact feed view; tap an article to open it in Reader Studio |
| Open an article | Show reader loading immediately, navigate to its source in a regular tab, follow HTTP redirects within that tab, then extract the loaded page; Back cancels pending extraction |
| Enable feed notifications | Ask for Android notification permission when needed; notify once per feed using stable tag and ID |
| A later article arrives | Replace the existing notification for that feed and update its title/body without starting a second alert |
| Add RSS widget | Show the 50 latest cached articles with their source names in a scrollable list; tapping an article opens it in Reader Studio |
| Resize RSS widget | More height reveals more article rows; shorter widgets keep the remaining articles reachable by scrolling, with the header and Discover feeds action fixed |
| Widget Discover feeds | Open the profile-owned source catalog; existing widget articles retain their Reader Studio handoff |
| Notification article | Open the Reader Studio Feeds inbox with the requested article above it; it is available again on Back |
| Change check interval | Settings → RSS feeds; WorkManager checks every 15 minutes to 24 hours, subject to Android scheduling |

## Ownership

| Concern | Owner |
| --- | --- |
| Feed and article bounds, URL checks, XML parsing, HTML entity cleanup | `reader/rss/RssModels.kt` |
| Feed persistence | `data/RssFeedStore.kt` |
| Read-state persistence and feed-scoped identity | `data/RssReadStore.kt`, `reader/rss/RssInboxRules.kt` |
| Serialized inbox I/O | `reader/rss/RssInboxRepository.kt` |
| Reader overview and lifecycle refresh | `ui/ReaderStudioHomeScreen.kt`, `ui/ReaderStudioHomeHost.kt` |
| Periodic network refresh and HTTP/XML charset decoding | `reader/rss/RssRefreshWorker.kt` |
| Stable notification replacement | `reader/rss/RssFeedNotifier.kt` |
| Homescreen widget | `RssFeedWidgetProvider`, `rss_feed_widget.xml`, `rss_feed_widget_article.xml` |
| Widget resize, scrolling, recent-article limit and empty-state regression coverage | `RssFeedWidgetLayoutInstrumentedTest` |
| Feed management and discovery | `RssFeedsActivity`, `ui/RssFeedScreens.kt` |
| Refresh preference | `data/RssSettingsStore.kt`, Settings → RSS feeds |
| Reader handoff and one-shot launch | `RssReaderNavigation`, `RssReaderLoadRules`, `IncomingLinkRouter.openInReader`, existing Reader Studio extraction flow |

## Privacy and limits

Subscriptions are device-local and shared across regular Candy profiles. Private tabs cannot start
feed discovery or save subscriptions. Background refresh uses direct HTTP(S) requests without
browser cookies, so feeds that require a signed-in session are unsupported. The widget shows cached
article titles and source names; article content is loaded by the existing Reader Studio from the
publisher page. RSS support is Android-only in this first version. Android may defer work, and its
notification permission can be denied independently from per-feed opt-in.

The widget and feed data are not included in Candy Sync. The app caps storage at 50 feeds and 40
articles per feed. XML payloads are limited to 2 MiB; redirects, timeouts, and response counts are
bounded.

The overview reads only the regular library and subscriptions. Its tab-switcher entry is absent in
private mode; the existing private article reader stays memory-only. Explicit widget/notification
launches create a regular tab when needed. Read keys are bounded to the articles still present in
the local feed cache and use feed-scoped SHA-256 identities. They are local and are not synced.
Opening an article does not mark it read if page loading or extraction fails.

The list displays the complete cache, with an explicit end marker. Standard RSS does not provide a
universal historical paging API; an unlimited archive is not implied. Feed-store changes and return
from feed management refresh the visible overview. Article selection keeps both list states alive
under the reader, and saved snapshots restore the latest recorded reading progress.

## Design concepts

| Surface | Prototype |
| --- | --- |
| Reader overview and searchable source selector | [`../design-concepts/rss/reading-options.html`](../design-concepts/rss/reading-options.html) |
| Proposed responsive 4×2 / 4×4 widget, light/dark/empty states | [`../design-concepts/rss/widget-options.html`](../design-concepts/rss/widget-options.html) |

The widget sizing concept is a proposal; its visual redesign is not implemented by the inbox change.
