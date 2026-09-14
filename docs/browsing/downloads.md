# Downloads

## Ownership

| Layer | Responsibility | Main code |
| --- | --- | --- |
| Model and policy | Status, backend control capabilities, newest-first ordering, text search, time filters, progress and safe download subfolders | `data/DownloadHistoryRules.kt`, `data/DownloadRuntimeRegistry.kt`, `data/BrowserDownloadSettings.kt` |
| Android data edge | Merge Candy-owned `DownloadManager` and Gecko `MediaStore` rows; open and clear files | `data/DownloadRepository.kt` |
| Activity and UI | Poll while visible, render progress, shared library search/filter and confirm cleanup | `DownloadsActivity.kt`, `ui/DownloadsScreen.kt`, `ui/LibrarySearchBar.kt` |
| Browser integration | Open Downloads from the browser `…` menu | `shared/browser/BrowserFeatureMenu.kt`, `ui/BrowserMainMenu.kt` |

## Behavior

- Downloads is a separate, non-exported activity opened from the browser `…` menu beside Favorites and History.
- Candy-owned downloads from Android `DownloadManager` and Gecko's scoped `MediaStore` writer appear together. Downloads handed to an external manager remain owned and tracked by that app.
- Entries are ordered by last update, newest first. The Material 3 search bar filters file names and source URLs live. Time chips filter to today or the last 7 or 30 local calendar days.
- Active downloads refresh while the screen is visible. The Material Expressive wavy indicator shows determinate progress for known total sizes and indeterminate progress for unknown totals.
- Active built-in downloads expose **Cancel**, including Android `DownloadManager`, Gecko streams and System WebView blob transfers. Cancellation removes incomplete storage; completed rows cannot be cancelled by stale UI actions.
- Gecko streams additionally expose **Pause** and **Resume**. Pause cooperatively stops reading the original authenticated response between chunks; it is process-only, keeps the connection open and does not add Range re-fetch or durable resume. A server timeout can still fail a paused transfer. Android `DownloadManager` has no public pause/resume control; blob transfers likewise expose cancellation only.
- Engine-originated downloads, context-menu downloads and authorized external-preview APK downloads respect the configured built-in/external/ask-every-time manager. Choosing built-in or falling back after an unavailable external app consumes the original engine response, preserving authentication and avoiding a second network request. Dismissing a choice releases that response. Engine-local `blob:` URLs stay inside the owning engine.
- Deferred choices capture the source renderer/navigation identity and context referrer. Closing or navigating the source rejects both built-in and external actions; stale preview responses are released without dismissing newly navigated preview content.
- Completed rows open through their content URI. Failed, paused and active rows are not opened.
- **Clear** requires confirmation, deletes completed and failed files from both local backends, and never cancels pending, running or paused downloads.
- When Candy’s built-in downloader is selected, Download settings can choose a nested folder below the public Downloads directory. The setting is applied to Android `DownloadManager`, System WebView and Gecko `MediaStore` transfers; unsupported locations are rejected and Downloads remains the safe default.
- System WebView resolves same-origin `blob:` downloads inside the page that created them, then streams bounded chunks into scoped `MediaStore` storage. This supports generated images and files whose temporary URL cannot be handed to Android `DownloadManager`.

## Privacy

- Candy adds no download-history persistence and stores no cookies, referrers or profile metadata for this screen.
- The repository reads only downloads owned by Candy's Android package. Private Gecko metadata stays at the existing transfer boundary rather than entering browser persistence.
