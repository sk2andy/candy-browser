# App data archive

## Ownership

| Concern | Owner |
| --- | --- |
| ZIP format, bounds and path validation | `data/AppDataArchiveRules.kt`, `data/AppDataArchiveCodec.kt` |
| Import staging and rollback swap | `data/AppDataArchiveStaging.kt`, `data/AppDataArchiveRestore.kt` |
| Storage Access Framework launch and confirmation UI | `MainActivity.kt`, `ui/SettingsScreen.kt`, `ui/AppDataArchiveDialogs.kt` |
| Cold export/import process | `AppDataTransferActivity.kt` |
| Android Auto Backup policy | `res/xml/data_extraction_rules.xml` |

## Archive format

Candy archive format v1 is a ZIP with `manifest.json` as its first entry and persistent app
files under `data/`. The manifest records archive format, application ID, Candy version,
Android SDK, WebView version and export time. ZIP entry CRCs are verified while staging and
again while extracting.

The exporter walks the app data directory rather than maintaining a list of individual
settings. New SharedPreferences, file stores, databases and WebView profile files therefore
join future exports automatically. Top-level `cache`, `code_cache`, `lib` and `app_textures`
are excluded as transient or installed artifacts.

## Included data

| Data | Examples |
| --- | --- |
| Candy settings | Appearance, search, downloads, protection and domain-specific settings |
| Browser state | Regular tabs, profiles, history, favorites, trails, snoozes and previews |
| Local content | Reader library, Site Capsules, icons, userscripts and Candy Rules |
| Website state | Best-effort raw WebView cookies, local storage, IndexedDB, CacheStorage and profile permissions |

Private in-memory state, Android runtime permissions, default-browser role, notification
settings, system password/passkey stores, public downloads and APK assets are not portable
app data and are not archived. Export is blocked while private tabs exist so an active
incognito WebView profile cannot enter the ZIP.

## Safety and compatibility

- Archives are plain ZIP files. They contain login sessions, browsing data, permission
  decisions, screenshots and executable userscripts. Export requires a sensitive-data warning.
- Import stages and fully validates the ZIP before showing confirmation. Absolute paths,
  traversal, duplicate entries, symbolic links and bounded-size violations reject the whole
  archive.
- Unknown archive-format versions are rejected. Candy, application ID, Android SDK or WebView
  mismatches show an explicit warning before the user can continue.
- Raw Chromium/WebView storage has no public portable serialization API. Cookies and website
  storage are best-effort across Android or WebView versions and may be discarded by WebView
  after a mismatched import.
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

Device-to-device transfer additionally includes all `filesDir` and database content. Raw
WebView profiles are deliberately excluded from automatic backup: they contain login tokens,
are provider-specific and can exceed Android's 25 MB cloud quota. The manual ZIP remains the
full, user-controlled route for cookies and website storage. Auto Backup timing and restore
availability are controlled by Android and the active backup transport.
