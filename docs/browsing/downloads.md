# Downloads

## Ownership

| Layer | Responsibility | Main code |
| --- | --- | --- |
| Model and policy | Status, newest-first ordering, text search, time filters, progress and safe download subfolders | `data/DownloadHistoryRules.kt`, `data/BrowserDownloadSettings.kt` |
| Android data edge | Merge Candy-owned `DownloadManager` and Gecko `MediaStore` rows; open and clear files | `data/DownloadRepository.kt` |
| Activity and UI | Poll while visible, render progress, shared library search/filter and confirm cleanup | `DownloadsActivity.kt`, `ui/DownloadsScreen.kt`, `ui/LibrarySearchBar.kt` |
| Browser integration | Open Downloads from the browser `…` menu | `shared/browser/BrowserFeatureMenu.kt`, `ui/BrowserMainMenu.kt` |

## Behavior

- Downloads is a separate, non-exported activity opened from the browser `…` menu beside Favorites and History.
- Candy-owned downloads from Android `DownloadManager` and Gecko's scoped `MediaStore` writer appear together. Downloads handed to an external manager remain owned and tracked by that app.
- Entries are ordered by last update, newest first. The Material 3 search bar filters file names and source URLs live. Time chips filter to today or the last 7 or 30 local calendar days.
- Active downloads refresh while the screen is visible. The Material Expressive wavy indicator shows determinate progress for known total sizes and indeterminate progress for unknown totals.
- Completed rows open through their content URI. Failed, paused and active rows are not opened.
- **Clear** requires confirmation, deletes completed and failed files from both local backends, and never cancels pending, running or paused downloads.
- When Candy’s built-in downloader is selected, Download settings can choose a nested folder below the public Downloads directory. The setting is applied to Android `DownloadManager`, System WebView and Gecko `MediaStore` transfers; unsupported locations are rejected and Downloads remains the safe default.

## Privacy

- Candy adds no download-history persistence and stores no cookies, referrers or profile metadata for this screen.
- The repository reads only downloads owned by Candy's Android package. Private Gecko metadata stays at the existing transfer boundary rather than entering browser persistence.
