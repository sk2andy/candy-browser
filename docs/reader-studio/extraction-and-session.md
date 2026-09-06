# Extraction and session

## Extraction pipeline

| Stage | Source | Boundary |
| --- | --- | --- |
| DOM selection | `ReaderExtractionScript` / Candy's internal Gecko content script | Clone `article`, `main`, or body; remove active/non-article elements |
| Engine result decode | `ReaderExtractionParser` | Decode WebView-quoted or Gecko raw JSON; bound size and shape; map malformed results to typed failure |
| Sanitization | `ReaderExtractionContract` | Accept only HTTP(S) source/links; normalize text; cap blocks, chars and links |
| Session | `ReaderStudioSessionRules` | Bind extraction to selected tab and request ID; reject stale results |

## Contract bounds

| Data | Limit |
| --- | ---: |
| Blocks | 600 |
| Characters per block | 12,000 |
| Total characters | 500,000 |
| Total links | 500 |

## Result handling

| Result | UI behavior |
| --- | --- |
| `Success` | Open reader with sanitized `ReaderDocument` |
| `UnsupportedPage` | Keep browser page and report unsupported source |
| `EmptyArticle` | Report that readable content was not found |
| `InvalidResponse` | Ignore malformed/stale engine payload and report failure |

Keep DOM extraction conservative. Add parser/contract cases before expanding supported markup.

Android Gecko routes extraction through the existing session-bound Candy Privacy WebExtension. The
extension resolves the native session token to the matching Gecko tab, runs only in the top frame,
and returns bounded plain data. `BrowserController` rejects results after tab, URL, navigation-generation,
or engine-session changes. Private-page results remain memory-only under the existing Reader Studio rules.
