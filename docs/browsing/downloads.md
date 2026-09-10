# Downloads

## Ownership

| Layer | Responsibility | Main code |
| --- | --- | --- |
| Model and policy | Status, newest-first ordering, text search, time filters and progress | `data/DownloadHistoryRules.kt` |
| Android data edge | Merge Candy-owned `DownloadManager` and Gecko `MediaStore` rows; open and clear files | `data/DownloadRepository.kt` |
| Activity and UI | Poll while visible, render progress, search/filter and confirm cleanup | `DownloadsActivity.kt`, `ui/DownloadsScreen.kt` |
| Browser integration | Open Downloads from the browser `…` menu | `shared/browser/BrowserFeatureMenu.kt`, `ui/BrowserMainMenu.kt` |

## Behavior

- Downloads is a separate, non-exported activity opened from the browser `…` menu beside Favorites and History.
- Candy-owned downloads from Android `DownloadManager` and Gecko's scoped `MediaStore` writer appear together. Downloads handed to an external manager remain owned and tracked by that app.
- Entries are ordered by last update, newest first. Search always matches the file name and also matches the source URL while its backend supplies one. Time chips filter to today or the last 7 or 30 local calendar days.
- Active downloads refresh while the screen is visible. Known total sizes use determinate progress; unknown totals use indeterminate progress.
- Completed rows open through their content URI. Failed, paused and active rows are not opened.
- **Clear** requires confirmation, deletes completed and failed files from both local backends, and never cancels pending, running or paused downloads.

## Privacy

- Candy adds no download-history persistence and stores no cookies, referrers or profile metadata for this screen.
- The repository reads only downloads owned by Candy's Android package. Private Gecko metadata stays at the existing transfer boundary rather than entering browser persistence.
