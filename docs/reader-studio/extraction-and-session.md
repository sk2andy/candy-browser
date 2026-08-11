# Extraction and session

## Extraction pipeline

| Stage | Source | Boundary |
| --- | --- | --- |
| DOM selection | `ReaderExtractionScript` | Clone `article`, `main`, or body; remove active/non-article elements |
| WebView result decode | `ReaderExtractionParser` | Bound JSON size and shape; map malformed results to typed failure |
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
| `InvalidResponse` | Ignore malformed/stale WebView payload and report failure |

Keep DOM extraction conservative. Add parser/contract cases before expanding supported markup.

