# Favorites

## Behavior and ownership

| Concern | Owner | Invariant |
| --- | --- | --- |
| Android collection | `FavoriteEntry`, `BrowsingLibraryRules`, `BrowserSessionStore` | Keep at most 100 local canonical HTTP(S) favorites. Favorites are global rather than profile-owned. Private page state never creates a persistent favorite; explicit library management may remove an existing one. |
| iOS collection | `BrowserViewModel`, `BrowserFavoritesRules` | Reflect the existing session-only favorite URL set; the library must not imply persistence that the iOS browser model does not provide yet. |
| Library entry point | `BrowserFeatureMenuRules`, `BrowserMainMenu`, `MainActivity` | The browser section of the ⋮ menu opens the Favorites library. |
| Search | `BrowsingFavoritesRules`, `FavoritesScreen`, `LibrarySearchBar`, `BrowserFavoritesRules`, `BrowserFavoritesSurface` | Filter locally and case-insensitively. Android searches stored title, URL and host, bounds input to 256 characters, discards invalid persisted URLs and collapses canonical duplicates. iOS searches its derived host title and URL. |
| Delete and undo | `FavoritesActivity`, `FavoriteUndoRules`, `FavoritesScreen`, `BrowserFavoritesSurface` | Delete one favorite and offer undo. Android commits library deletion off-main before updating the list and showing its Snackbar; restore the exact previous list only while the mutation revision is still current. iOS provides the equivalent timed bottom banner for its session collection. |
| Open | `FavoritesActivityContract`, `BrowserController.openFavorite` | Validate the selected URL again at the browser boundary. A selection made from private browsing opens in a regular tab so persistent library data is not navigated inside a private session. |
| Refresh | `MainActivity`, `BrowserController.reloadFavorites` | Reload controller state whenever the library activity returns, including Back after a deletion. Increment the mutation revision so an older browser Snackbar cannot overwrite library changes. |

## Test lookup

| Surface | Tests |
| --- | --- |
| Search, validation, deduplication and removal | `BrowsingFavoritesRulesTest` |
| Exact and stale undo | `FavoriteUndoRulesTest` |
| Search, row deletion, Snackbar undo and opening | `FavoritesScreenInstrumentedTest` |
| Activity navigation result | `FavoritesActivityContractInstrumentedTest` |
| Shared menu order and Android rendering | `BrowserFeatureMenuRulesTest`, `BrowserMainMenuInstrumentedTest` |
