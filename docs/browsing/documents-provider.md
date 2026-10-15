# Android document picker

| User action | Behavior |
| --- | --- |
| Another app → Open / Attach file → Candy | Browse Reader articles and completed Candy downloads |
| Select Reader article | Generate a paginated, text-only PDF offline, including original source URL |
| Select download | Open the existing local file with a read-only descriptor |

## Privacy and ownership

| Boundary | Contract |
| --- | --- |
| Discovery | Exported `CandyDocumentsProvider` requires Android's signature-level `MANAGE_DOCUMENTS` permission; ordinary apps access only documents explicitly granted through the system picker |
| Profiles | Every list/open rechecks the persisted source profile; protected, synced and deleted profiles are excluded, including previously granted document URIs |
| Private content | Private Reader snapshots are never persisted. Private downloads never receive document-picker provenance |
| Legacy content | Existing articles/downloads without source-profile provenance remain available in Candy but do not enter the system picker |
| Reader provenance | Android Reader Studio, save-page and Link Peek saves record the original regular profile ID |
| Saving existing offline content | Reader Studio preserves the stored article's original profile or unknown legacy provenance; opening it from another profile never relabels it for external access |
| Download provenance | Android DownloadManager, Gecko streams and System WebView blob transfers record only download ID, filename and regular profile ID in bounded device-local metadata; no cookies, URLs or private transfer records |
| Downloads | Only successful Candy-owned files with matching provenance and an existing readable `content:` descriptor are shown; pending/failed/missing files are excluded |
| Read-only | No create, rename, delete or write flags; all modes other than `r` fail |
| Document IDs | Canonical UUID/numeric IDs resolved through current stores; callers cannot supply filesystem paths or arbitrary content URIs |
| PDF generation | Android canvas/text, no WebView, scripts or remote fetches; line-aligned A4 pages, bounded to 200 pages; cancellation checked between pages |
| Temporary PDF | Seekable app-cache file is unlinked immediately after its read descriptor opens and on failures; no durable PDF export is retained |
| Existing open descriptors | An already-open descriptor remains readable until its recipient closes it; profile changes prevent subsequent opens |
| Portability | Download provenance stays outside Auto Backup and manual export/import because local backend IDs do not migrate |
| Transfer lock | App-data transfer suppresses document contents while persistent state is unavailable |

## Ownership and verification

| Concern | Owner / test |
| --- | --- |
| System contract, grants and descriptor mode | `CandyDocumentsProvider` |
| ID parsing and safe PDF names | `CandyDocumentRules`, `CandyDocumentRulesTest` |
| Profile/file revalidation | `CandyDocumentsRepository` |
| Offline PDF layout | `ReaderPdfExporter` |
| Download eligibility persistence | `DownloadProvenanceStore` |
| Provider browsing, seekable PDF, protection, malformed IDs and completed/private/pending downloads | `CandyDocumentsProviderInstrumentedTest` |

Framework reference: [DocumentsProvider](https://developer.android.com/reference/android/provider/DocumentsProvider), [custom document provider](https://developer.android.com/guide/topics/providers/document-provider).
