# Candy Recall

## Product contract

Candy Recall is an optional, local full-text index of readable page text. It is disabled by
default. Enabling it explains that Candy stores readable text from regular pages on the device
so it can be searched later.

| Entry point | Scope | Behavior |
| --- | --- | --- |
| Normal address input | Active regular profile | Two or more meaningful words return at most two Recall matches in a localized **From your history** section before remote web suggestions. |
| `>recall <query>` | Active regular profile | Searches only the local Recall index. It never falls through to navigation or a remote search/suggestion request. |
| History search | Profiles selected in History | Existing title, host and URL matches are combined with full-text Recall matches and bounded matching excerpts. |

Recall is absent in private tabs. Private input does not query the index, and private pages, Link
Peek, and federated-login popup pages never enter it.

## Ownership

| Layer | Responsibility | Main code |
| --- | --- | --- |
| Model and policy | Input normalization, eligibility, bounds, query parsing and stale extraction identity | `recall/RecallModels.kt` |
| Extraction | Bounded normal-page DOM text extraction and WebView result parsing | `recall/RecallExtraction.kt` |
| Persistence and ranking | Serialized SQLite FTS4 writes, profile-scoped queries, deterministic lexical ranking and pruning | `data/RecallRepository.kt` |
| Browser integration | Committed-page extraction, stale callback rejection, active-profile address queries and cleanup wiring | `browser/BrowserController.kt` |
| Address and History UI | Section ordering, local-only command submission and matching excerpts | `ui/BrowserScreen.kt`, `HistoryActivity.kt`, `ui/HistoryScreen.kt` |
| Setting | Disabled-by-default opt-in and clear-on-disable action | `data/BrowserSessionStore.kt`, `ui/SettingsScreen.kt` |

## Indexing and ranking

- Only committed HTTP(S) pages in regular tabs are eligible. A page must still match its captured
  tab, profile, canonical URL, navigation generation and WebView when extraction returns. Recall
  must still be enabled and the tab must still be regular.
- Extraction traverses at most 20,000 live, rendered text nodes from normal pages rather than
  Reader Studio's article-block model. Script, style, form, navigation, side content, frames,
  embedded objects, media and CSS-hidden nodes are skipped before text is normalized.
- Stored rows are isolated by profile and canonical URL. Re-indexing the same URL in one profile
  replaces only that profile's row.
- SQLite FTS4 with the platform `unicode61` tokenizer performs prefix lexical matching. Ranking
  weights title hits above body hits, then uses visit time, profile ID and URL as deterministic
  tie-breakers. There is no network, model, embedding or cloud dependency.

## Bounds

| Value | Limit |
| --- | ---: |
| Stored documents | 250 total, matching browsing-history retention |
| Normalized document text | 64,000 characters |
| Title | 512 characters |
| Profile ID | 128 characters |
| Canonical URL | 4,096 characters |
| Query | 160 characters / 12 meaningful terms |
| Address results | 2 |
| Explicit-command results | 20 |
| History results | 50 |
| Matching excerpt | 320 characters |

Terms shorter than two letters or digits are ignored. Normal address Recall starts only when at
least two distinct meaningful terms remain. Repository work runs on its serialized executor and
returns bounded results to the main thread.

## Privacy, cleanup and transfer

- Recall cleanup follows its source history. Individual deletion, inclusive range deletion,
  clear-on-exit and profile deletion remove corresponding profile-scoped Recall rows.
- Disabling Recall clears the index by deleting its database and SQLite sidecars; clearing Recall
  does not enable it. Partial deletion uses SQLite secure deletion. A corrupt or unavailable
  database fails closed and must not expose another profile's rows.
- The database lives under top-level `no_backup`. Manual ZIP path rules reject the exact Recall
  database and SQLite sidecar paths while preserving unrelated local stores. Android cloud backup
  and device-to-device transfer exclude `noBackupFilesDir`. Recall page text is therefore never
  exported, imported or restored through app-data transfer. Import and interrupted-import recovery
  clear any pre-existing local Recall index.
- Recall can rebuild only from later committed eligible pages. Candy does not silently reconstruct
  page text from history URLs and does not fetch pages in the background.

## Verification lookup

| Contract | Tests |
| --- | --- |
| Query parsing, bounds, canonical HTTP(S) eligibility and stale identities | `recall/RecallRulesTest`, `recall/RecallExtractionParserTest` |
| FTS ranking across the full bound, profile isolation, pruning, corruption, cleanup races and full storage deletion | `data/RecallRepositoryInstrumentedTest` |
| Disabled-by-default setting and clear behavior | `BrowserSessionStoreInstrumentedTest`, `ui/SettingsScreenInstrumentedTest` |
| Address section, local-only command and History excerpts | Address/History rule tests and focused Compose instrumented tests |
| Manual and Android transfer exclusion | `AppDataArchiveRulesTest`, `AppDataArchiveCodecTest`, Android resource validation in lint/assemble |
