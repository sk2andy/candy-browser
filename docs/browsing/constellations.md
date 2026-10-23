# Candy Constellations

## Product contract

| Concern | Contract |
| --- | --- |
| Entry | Android browser menu → Candy Constellations. Available for accessible regular local profiles, including empty tabs. |
| Profile scope | Each map and opt-in belongs to exactly one local profile. No all-profile query, ranking, graph or navigation path exists. |
| Opt-in | Disabled per profile by default. Disabling deletes that profile's documents and embeddings. |
| Model | Separate explicit download of about 138 MB from Hugging Face; immutable revision, exact byte bounds and SHA-256 hashes. One public model shared across profiles. Page content is never sent to a model service. |
| Inference | Local ONNX CPU inference, cased multilingual WordPiece, mean pooling and released dense projection. At most three bounded windows cover beginning/middle/end; page-level 512-dimensional normalized embedding. |
| Graph | Up to 180 pages per profile. Cosine similarity ≥ 0.62, strongest-first edges, maximum degree 3. Content-anchored deterministic force layout. Similarity means topic relatedness, not agreement or truth. |
| Interaction | Expandable map with pan, pinch/explicit zoom and Show all. A separate accessible page list and persistent lower details panel leave the map available; selecting a page focuses its neighborhood and dismisses the keyboard. Opening a page preserves the owning active profile. |
| Topics | Connected components of existing similarity edges with at least two pages become navigable topic chips. Names quote recurring saved-title words, or a representative saved title; no generated claims or remote requests. Isolated pages remain visible under All topics. |
| Graph presentation | Theme-tinted group halos, selected rings and neighborhood emphasis; local expressive motion scheme with spatial camera/node springs and effects emphasis. Labels use a viewport/zoom budget, prioritizing visible selection, search matches and neighbors before collision avoidance. |
| Updating | Keep only unchanged, still-authorized documents from the previous graph while recomputing. Deleted/changed/foreign nodes and their edges disappear immediately; disabling or changing profile clears UI state. |
| Layout | Optional expanded map hides topic chips and secondary heading; compact-height detail views hide search to retain map context. Graph dimensions and panel height are Candy presentation choices, not Material component tokens. |
| Keyword search | Local title, URL and saved excerpt search within the current profile. Case and accents ignored; all query words must match. Matching nodes highlighted, surrounding graph dimmed; previous/next centers each match. Query capped at 160 characters and cleared when the profile changes. Search saves the explored viewport and restores it on clear; explicitly selecting a page or topic takes precedence. |
| Search presentation | Material 3 `SearchBar` and default input field, matching existing library/settings search. Persistent rounded surface with inline placeholder; graph remains visible while typing. Submitting hides the keyboard and clears focus. |
| Rebuild | Only committed readable pages; model setup can index the currently loaded page on return without refetching. Existing history URLs are never fetched in the background. |
| Platforms | Android Gecko and System WebView. iOS menu capability disabled until a native inference adapter exists. |

## Privacy and lifecycle

| Boundary | Enforcement |
| --- | --- |
| Private/temporary | Private tabs, Link Peek, external previews and unbound popup sessions cannot extract/index. Promoted previews become eligible only after explicit regular-tab adoption. |
| Extraction | At most 20,000 rendered text nodes, 64,000 text characters and 150,000 payload characters. Forms, editable content, navigation, frames, scripts, media and hidden content excluded. |
| First layout | Empty/unavailable extraction gets at most two retries after 250 ms, retaining the original document identity and cleanup epoch. |
| Async identity | Tab, profile, canonical HTTP(S) URL, navigation generation, session identity, foreground state, enablement and cleanup epoch validated before accepting extraction. |
| Protected profile | Access checked before extraction, inference, commit and snapshot delivery. Map activity rejects missing/locked profiles and suppresses Recents screenshots for protected profiles. |
| Guest Bubble | Durable guest owner lock rejects map entry, extraction, inference and snapshot access; the map activity redirects to the guest gate. |
| Source retention | Embedding commit requires its profile-qualified source history entry still exists. Disabled history recording does not index new pages. |
| Visit order | Pending and persisted documents preserve the newest visit for each profile-qualified URL; late callbacks cannot replace newer content. Bounded captures wait for initial model verification. |
| Cleanup | History entry/range/profile deletion, eviction, clear-on-exit and full history clear remove matching embeddings. Epoch invalidation rejects in-flight results captured before cleanup. |
| Storage | SQLite `no_backup/candy_constellations.db`; composite key `(profile_id,url)`, secure deletion, bounded excerpt and vector. Settings are inside same local database. |
| Transfer | Database/sidecars and `no_backup/constellation_model/` excluded from manual archives and Android backup. Import/recovery resets pre-existing map state. No Constellations data enters Candy Sync. |
| Work bounds | Four pending documents, one inference worker, at most four documents per native model session; native resources released between batches. Download/inference never block the serialized storage executor. |

## Ownership

| Layer | Main source |
| --- | --- |
| Model, graph, layout, search | `constellations/ConstellationModels.kt`, `ConstellationGraphRules.kt`, `ConstellationGraphLabelRules.kt`, `ConstellationSearchRules.kt`, `ConstellationPresentationRules.kt` |
| Model/tokenizer/download | `constellations/ConstellationEmbedder.kt`, `ConstellationWordPieceTokenizer.kt`, `ConstellationModelFiles.kt`; [pinned model details](constellations-model.md) |
| Storage/orchestration | `data/ConstellationStore.kt`, `ConstellationRepository.kt` |
| Browser wiring | `BrowserController.indexConstellationPage`, `BrowsingHistoryRepository` |
| Engine extraction | `PageTextExtraction`, `GeckoTextExtractionRequest`, Gecko privacy bridge, System WebView session |
| UI/navigation | `ConstellationActivity`, `ConstellationActivityContract`, `ui/ConstellationScreen.kt`, `ui/ConstellationExplorer.kt`, `ui/ConstellationMap.kt`, shared menu capability |

## Verification lookup

| Contract | Tests |
| --- | --- |
| Graph isolation, similarity, degree, malformed vectors, bounds, layout and zoom | `ConstellationGraphRulesTest` |
| Non-overlapping labels, viewport budgets, local topic groups, retained-graph deletion boundaries and profile-scoped keyword matching | `ConstellationGraphLabelRulesTest`, `ConstellationPresentationRulesTest`, `ConstellationSearchRulesTest` |
| Tokenization and three-window bounds | `ConstellationWordPieceTokenizerTest` |
| Pinned actual Android CPU inference | `ConstellationEmbedderInstrumentedTest`; requires staged verified model fixtures, no network download in test |
| SQL isolation, opt-in, retention, deletion and malformed input | `ConstellationStoreInstrumentedTest`, `BrowserControllerConstellationsInstrumentedTest` |
| Extraction schema, scripts and real engines | `PageTextExtractionTest`, `scripts/constellations_extraction.test.mjs`, `GeckoReaderViewInstrumentedTest`, `SystemWebViewPageTextInstrumentedTest` |
| UI opt-in/download, persistent details, expansion, deletion, search navigation/viewport restoration and profile replacement | `ConstellationScreenInstrumentedTest` |
| Bounded first-layout retries and stale owner rejection | `ConstellationPageExtractionInstrumentedTest` |
| Menu capability/profile availability and placement | shared `BrowserFeatureMenuRulesTest`, `BrowserMenuLayoutRulesTest` |
| Transfer exclusion | `ConstellationArchiveRulesTest` |
