# Device search

## User flow

| Step | Behavior |
| --- | --- |
| Open | Settings → Browser → Device search |
| Consent | Disabled by default; users individually select bookmarks and saved Reader articles, then enable publication |
| Bookmark destination | Users choose an eligible local profile for global bookmarks, which have no original profile provenance |
| Open result | A bounded opaque item deeplink revalidates current consent, saved source and profile before creating a regular tab |
| Saved article | Opens the persisted Reader document offline in its recorded profile; the original website is not fetched |
| Disable | Revoke system schema visibility and remove published documents |

## Boundaries and lifecycle

| Concern | Contract |
| --- | --- |
| Content | Only selected titles and source URLs; no visit history, Recall text, article body, private state, passwords or cookies |
| Profiles | Existing, unprotected local profiles only; hidden profiles are excluded when profiles are disabled |
| Reader provenance | Private snapshots, unknown legacy ownership, deleted owners and synced owners cannot be published |
| Retraction | Bookmark/profile preference changes and atomic Reader-file changes rebuild the index; removed or ineligible item selections are pruned, so making a profile public again does not restore consent automatically |
| Async identity | Source revision and incoming-request generation reject stale navigation callbacks |
| Visibility | Dedicated AppSearch database; visibility is revoked before replacement and enabled only after a complete current write |
| Platform schema | `builtin:WebPage` with nested `builtin:PotentialAction`; opaque Candy deeplinks preserve source validation rather than exposing a bypass URL |
| Refresh | Local WorkManager job every three hours; no network constraint or network requests. Disable cancels recurring work |
| Expiry | Documents expire after six hours if refresh cannot run; Android may delay background work and temporarily remove results |
| Device support | Android platform AppSearch, API 33+; actual discovery/rendering depends on a compatible system search/launcher. Pixel Launcher support is not assumed |
| Transfers | Source reads and result opening reject active app-data transfers; consent preferences are excluded from Android cloud backup, device transfer and Candy ZIP export/import |
| Localization | Prototype English/German strings; other app locales use English fallback |

## Ownership and tests

| Concern | Code / tests |
| --- | --- |
| Eligibility and consent | `DeviceSearchRules`, `DeviceSearchRulesTest` |
| Metadata preferences | `DeviceSearchStore` |
| Platform indexing and lifetime observers | `DeviceSearchIndexer`, `DeviceSearchRefreshWorker`, `DeviceSearchIndexerInstrumentedTest` |
| Source and navigation validation | `DeviceSearchSources`, `DeviceSearchContract`, `DeviceSearchNavigator` |
| Settings | `DeviceSearchSettingsActivity`, `DeviceSearchSettingsPage`, `DeviceSearchSettingsPageInstrumentedTest` |
| Reader handoff | `BrowserController.deviceSearchReaderSnapshot`, focused `BrowserScreen` effect |
| Archive exclusion | `AppDataArchiveRulesTest` |

## Platform references

| API | Reference |
| --- | --- |
| System display opt-in | [SetSchemaRequest.Builder](https://developer.android.com/reference/android/app/appsearch/SetSchemaRequest.Builder#setSchemaTypeDisplayedBySystem(java.lang.String,boolean)) |
| Built-in webpage fields | [AndroidX WebPage](https://github.com/androidx/androidx/blob/androidx-main/appsearch/appsearch-builtin-types/src/main/java/androidx/appsearch/builtintypes/WebPage.java), [Thing](https://github.com/androidx/androidx/blob/androidx-main/appsearch/appsearch-builtin-types/src/main/java/androidx/appsearch/builtintypes/Thing.java) |
| Open actions | [AndroidX PotentialAction](https://github.com/androidx/androidx/blob/androidx-main/appsearch/appsearch-builtin-types/src/main/java/androidx/appsearch/builtintypes/PotentialAction.java) |
