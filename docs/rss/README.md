# RSS feeds

## Behavior

| User action | Result |
| --- | --- |
| Browser menu → RSS feeds on a regular page | Candy scans page markup for alternate RSS/Atom links and shows candidates; private tabs do not expose this action |
| Follow a feed | Fetch and parse it immediately, then save feed metadata and up to 40 article summaries locally |
| Open a followed feed | Show its latest articles in a dedicated, compact feed view; tap an article to open it in Reader Studio |
| Open an article | Navigate to its source in a regular tab, then hand the loaded page to Reader Studio extraction |
| Enable feed notifications | Ask for Android notification permission when needed; notify once per feed using stable tag and ID |
| A later article arrives | Replace the existing notification for that feed and update its title/body without starting a second alert |
| Add RSS widget | Show up to three latest cached articles with their source names; tapping an article opens it in Reader Studio |
| Change check interval | Settings → RSS feeds; WorkManager checks every 15 minutes to 24 hours, subject to Android scheduling |

## Ownership

| Concern | Owner |
| --- | --- |
| Feed and article bounds, URL checks, XML parsing, HTML entity cleanup | `reader/rss/RssModels.kt` |
| Feed persistence | `data/RssFeedStore.kt` |
| Periodic network refresh and HTTP/XML charset decoding | `reader/rss/RssRefreshWorker.kt` |
| Stable notification replacement | `reader/rss/RssFeedNotifier.kt` |
| Homescreen widget | `RssFeedWidgetProvider`, `rss_feed_widget.xml` |
| Feed management and discovery | `RssFeedsActivity`, `ui/RssFeedScreens.kt` |
| Refresh preference | `data/RssSettingsStore.kt`, Settings → RSS feeds |
| Reader handoff | `IncomingLinkRouter.openInReader` and the existing Reader Studio extraction flow |

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
