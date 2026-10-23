# App data archive

## Ownership

| Concern | Owner |
| --- | --- |
| ZIP format, bounds and path validation | `data/AppDataArchiveRules.kt`, `data/AppDataArchiveCodec.kt` |
| Import staging and rollback swap | `data/AppDataArchiveStaging.kt`, `data/AppDataArchiveRestore.kt` |
| Storage Access Framework launch and confirmation UI | `MainActivity.kt`, `ui/ProtectionSettingsPage.kt`, `ui/AppDataArchiveDialogs.kt` |
| Cold export/import process | `AppDataTransferActivity.kt` |
| Android Auto Backup policy | `res/xml/data_extraction_rules.xml` |

## Archive format

Candy archive format v3 is a ZIP with `manifest.json` as its first entry and persistent app
files under `data/`. The manifest records archive format, application ID, Candy version,
Android SDK, browser-engine identity, export time and typed
`websiteState=engine-state-included` contract. ZIP entry CRCs are verified while staging and
again while extracting.

The exporter walks the app data directory rather than maintaining a list of individual
settings. New SharedPreferences, file stores and databases join future exports automatically
unless explicitly excluded. Format v3 additionally includes the standard Gecko profile, standard
WebView profile and persisted per-tab engine snapshots. The isolated Guest Bubble WebView profile
is excluded. Top-level `cache`, `code_cache`, `lib` and `app_textures` are excluded. Import path
validation and restore-root discovery enforce the same exclusions.

## Included data

| Data | Examples |
| --- | --- |
| Candy settings | Appearance, search (including Device Search selection), downloads, protection and domain-specific settings in persistent app stores |
| Browser state | Regular tabs, profiles, history, favorites, trails, snoozes and previews |
| Local content | Reader library, Site Capsules, icons, userscripts and Candy Rules |
| Custom workflows | `files/custom_workflows_v1.json`: named step sequences, icons, menu visibility and stable slot/gesture bindings, including references to deleted workflows |
| Profile presentation | Separate per-profile new-tab and tab-switcher wallpaper images with independent crop/zoom metadata |
| Website state (v3) | Gecko/WebView profile files and tab snapshots, including engine-managed cookies, local storage, IndexedDB, CacheStorage, login state and site permissions when stored by the engine. These are opaque engine files and are only attempted on restore; they are not a portable login format. |

Private in-memory state, Android runtime permissions, default-browser role, notification
settings, system password/passkey stores, public downloads and APK
assets are not portable app data and are not archived. Export is blocked while private tabs exist
so an active incognito WebView profile cannot enter the ZIP. Guest Bubble state and its isolated
WebView profile are also excluded.

Custom workflow definitions and bindings share one versioned atomic file, so archive restore
preserves both their IDs and placement. Deleting a workflow intentionally keeps its bindings:
restored unresolved button slots remain empty and unresolved link gestures do nothing. Import
does not execute workflows. The loader bounds and validates definitions; an unknown step rejects
its entire workflow rather than removing a prerequisite and leaving a later close step active.
Execution state, page input and results are never stored in this configuration file. Older
archives without it restore an empty workflow configuration through the existing cold-process
replacement boundary.

| Local-only data | Excluded archive paths |
| --- | --- |
| In-progress form drafts | `no_backup/form_drafts` and descendants; transient form contents stay local |
| Constellation graph/model cache | `no_backup/candy_constellations.db`, `no_backup/constellation_model` and descendants |
| Web annotations | `no_backup/web_annotations.json` and atomic-write sidecars |
| Candy Recall page text | `no_backup/candy_recall.db` and SQLite sidecars |
| App and Gecko diagnostics | `no_backup/app_logs`, `no_backup/gecko_logs` and descendants |
| Native log capture authorization | `shared_prefs/app_logging_capture.xml` and `.xml.bak` |
| Guest Bubble session and profile | `no_backup/guest_bubble` and `app_webview_guest_bubble` |
| Homescreen website widgets | `no_backup/site_widget_snapshots_v1` and descendants, including images, source metadata and launcher widget bindings |
| Download document-picker provenance | `no_backup/download_document_sources_v1.json` and its atomic backup; backend IDs remain device-local |
| Candy Sync encrypted vault/cache | `no_backup/candy_sync_vault_v1` and `no_backup/candy_sync_cache_v1`; ciphertext depends on an Android Keystore key that does not migrate |

These exclusions apply to both export and incoming archive entries. Website widget captures
remain device-local; launcher widget IDs and captured page pixels never migrate through the ZIP.

Candy Recall stores readable page text in `no_backup/candy_recall.db`. Manual export and import
rules reject that database and its SQLite journal/WAL sidecars by exact relative path while
preserving unrelated persistent `no_backup` stores. Imported History does not silently fetch or
reconstruct Recall text. Successful import and interrupted-import recovery clear any pre-existing
local Recall index so old page text cannot coexist with imported History.

App diagnostic logs under `no_backup/app_logs` and native capture authorization preferences
`shared_prefs/app_logging_capture.xml` / `.xml.bak` stay local. Export excludes them and import
rejects incoming entries at these paths, so a restored archive cannot grant access to another
device’s native crash history.

## Safety and compatibility

- Archives are plain ZIP files. They contain login sessions, browsing data, permission
  decisions, screenshots and executable userscripts. Export requires a sensitive-data warning.
- If any profile uses biometric access protection, Candy requests strong biometric authentication
  after the destination is selected and before transfer starts. The warning states explicitly that
  protected profile contents and raw engine files are readable in the ZIP and that the ZIP has no
  biometric or cryptographic protection. The current profile lock is not per-profile encryption
  at rest.
- Import stages and fully validates the ZIP before showing confirmation. Absolute paths,
  traversal, duplicate entries, symbolic links and bounded-size violations reject the whole
  archive.
- Unknown archive-format versions are rejected. Candy, application ID, Android SDK or WebView
  mismatches show an explicit warning before the user can continue.
- Raw Chromium/WebView and Gecko storage has no cross-engine portable serialization API. Format v3
  includes those raw roots, so import warns on a browser-engine identity mismatch. Even matching
  identity does not guarantee that a website accepts restored authentication: providers may bind
  tokens to the original device, encryption keys, or other environment details. Cookies and site
  storage are included where the engine persists them, but successful website login restoration is
  not guaranteed, especially across devices or engine versions. Format v2 remains accepted and
  restores Candy-owned files only. Format v1 remains accepted as a migration; recognized engine
  paths are skipped and `LegacyWebsiteStateExcluded` is reported by inspection and extraction.
  Unknown format versions still reject.
- Import replaces all current persistent roots. A same-filesystem backup is kept during the
  root swap and restored on failure. A durable journal outside archived roots makes an
  interrupted swap roll back before normal app startup.

## Process boundary

Returning from the document picker first lets `MainActivity` persist tab state and flush
cookies. A dedicated `:dataTransfer` process then stops the main process before reading or
replacing persistent roots. It relaunches `MainActivity` after export or import. `recreate()`
must not replace this cold boundary because WebView, cookie, database and SharedPreferences
caches are process-scoped.

A token-bound process lock gates every app entry point that could open persistent state while
the transfer process is active. If recovery cannot restore the previous roots, the transfer
screen remains open and preserves its journal and backup instead of launching mixed data.

## Android Auto Backup

Android Auto Backup is enabled as a smaller automatic restore path. Cloud backup requires
client-side encryption and includes shared preferences, Candy Rules, userscripts and Site
Capsules. This covers settings, domain exceptions, regular tabs, snoozes and Filter Studio
state. Rules and userscripts migrate once from `noBackupFilesDir` to `filesDir` so Android can
include them.

Custom workflow definitions and bindings are also explicitly included in encrypted cloud backup;
the file-wide device transfer rule and manual ZIP export include them automatically.

The local native capture preferences `app_logging_capture.xml` and their `.xml.bak` copy are
explicitly excluded from cloud backup and device-to-device transfer alongside diagnostic logs.

Device-to-device transfer additionally includes all `filesDir` and database content. Android's
`noBackupFilesDir` remains outside automatic backup and transfer, so Candy Recall text stays on
its source device. Raw WebView profiles are deliberately excluded from automatic backup: they
contain login tokens,
are provider-specific and can exceed Android's 25 MB cloud quota. The manual ZIP remains the
full, user-controlled route for cookies and website storage. Auto Backup timing and restore
availability are controlled by Android and the active backup transport.

Manual archives include persistent Candy preferences except device-bound security and diagnostic data.
Runtime/system state and biometric keys cannot be copied as preferences. As with engine state,
restoring a profile lock preference does not migrate a device's secure keystore material. Device
Search preferences are included in manual archives; Android's separate automatic backup policy
still excludes them.

Profile wallpaper files remain outside cloud backup to avoid consuming Android's small shared
quota with user images. Each slot's profile metadata can still restore through shared preferences;
Candy removes only the metadata whose corresponding image is absent. Device-to-device transfer and
the manual ZIP archive include both Candy-owned wallpaper files per configured profile.
