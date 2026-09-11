# Favorites

## Behavior and ownership

| Concern | Owner | Invariant |
| --- | --- | --- |
| Android collection | `FavoriteEntry`, `BrowsingLibraryRules`, `BrowserSessionStore` | Keep at most 100 local canonical HTTP(S) favorites. Favorites are global rather than profile-owned. Private page state never creates a persistent favorite; explicit library management may remove an existing one. |
| iOS collection | `BrowserViewModel`, `BrowserFavoritesRules` | Reflect the existing session-only favorite URL set; the library must not imply persistence that the iOS browser model does not provide yet. |
| Library entry point | `BrowserFeatureMenuRules`, `BrowserMainMenu`, `MainActivity` | The browser section of the ⋮ menu opens the Favorites library. |
| Search | `BrowsingFavoritesRules`, `FavoritesScreen`, `LibrarySearchBar`, `BrowserFavoritesRules`, `BrowserFavoritesSurface` | Filter locally and case-insensitively. Android searches stored title, URL and host, bounds input to 256 characters, discards invalid persisted URLs and collapses canonical duplicates. iOS searches its derived host title and URL. |
| Android presentation | `FavoriteFaviconRepository`, `FavoriteFaviconStore`, `FavoritesScreen` | Reuse a local regular-tab favicon when available. Otherwise fetch the favored page origin's `/favicon.ico` once while adding the favorite, following same-origin redirects only; never contact a third-party favicon service or fetch from the library screen. Keep the favorite-specific cache independent of tab lifetime and render a neutral title initial when no icon is available. Keep list rows unseparated and use the shared Material 3 SearchBar treatment. |
| Android new tab | `BrowserController`, `NewTabPage`, `NewTabFavoriteGrid` | Hide the favorites surface when the collection is empty or browsing is private. Show its localized Favorites heading and a generously inset four-column favicon grid with a neutral title-initial fallback, including blank-tab previews. Size the surface for at most five rows (20 entries), shrink it to the available height, and scroll further entries inside the surface rather than scrolling the whole new-tab hero. Returning through the tab overview releases the non-interactive preview after the blank-tab content frame instead of waiting for a browser-engine frame that blank tabs never produce. |
| Favorite launch motion | `NewTabFavoriteLaunchOverlay`, `BrowserSettingsPage`, `BrowserSessionStore` | By default, animate a selected favorite through a short Candy-colored arc into the search hero before opening it. The Browser setting disables this motion and restores immediate navigation. |
| Delete and undo | `FavoritesActivity`, `FavoriteUndoRules`, `FavoritesScreen`, `BrowserFavoritesSurface` | Delete one favorite and offer undo. Android commits library deletion off-main before updating the list and showing its Snackbar; restore the exact previous list only while the mutation revision is still current. iOS provides the equivalent timed bottom banner for its session collection. |
| Open | `FavoritesActivityContract`, `BrowserController.openFavorite` | Validate the selected URL again at the browser boundary. A selection made from private browsing opens in a regular tab so persistent library data is not navigated inside a private session. |
| Refresh | `MainActivity`, `BrowserController.reloadFavorites` | Reload controller state whenever the library activity returns, including Back after a deletion. Increment the mutation revision so an older browser Snackbar cannot overwrite library changes. |

## Test lookup

| Surface | Tests |
| --- | --- |
| Search, validation, deduplication and removal | `BrowsingFavoritesRulesTest` |
| Exact and stale undo | `FavoriteUndoRulesTest` |
| Search, row deletion, Snackbar undo and opening | `FavoritesScreenInstrumentedTest` |
| New-tab grid bounds, internal scrolling, favicon rendering and launch motion | `NewTabFavoriteGridRulesTest`, `NewTabPageInstrumentedTest`, `NewTabFavoriteInstrumentedTest` |
| Launch-motion setting persistence and UI | `BrowserSessionStoreInstrumentedTest`, `BrowserSettingsScreenInstrumentedTest` |
| Activity navigation result | `FavoritesActivityContractInstrumentedTest` |
| Shared menu order and Android rendering | `BrowserFeatureMenuRulesTest`, `BrowserMainMenuInstrumentedTest` |
