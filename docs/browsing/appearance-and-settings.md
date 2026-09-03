# Appearance and settings

## Ownership

| Layer | Responsibility | Main code |
| --- | --- | --- |
| Model | Stable, persisted appearance choices and safe fallback values | `data/AppearanceSettings.kt` |
| Persistence | Global appearance preference round trips | `data/BrowserSessionStore.kt` |
| State | Observable selection and update wiring | `browser/BrowserController.kt` |
| Theme | Color schemes, Android night resources, website color-scheme preference, surface treatment, shape tokens and AMOLED surfaces | `MainActivity`, `ui/theme/MaterialBrowserTheme.kt` |
| UI | Appearance destination and live selection controls | `ui/SettingsScreen.kt` |
| Address-bar actions | Persisted ordered action layout plus drag-editor navigation under Tabs & gestures | `data/AddressBarActionLayout.kt`, `ui/AddressBarActionEditor.kt`, `BrowserSessionStore` |
| Page scroll bar | Persisted opt-in, WebView scroll metrics and draggable auto-hide overlay | `BrowserSessionStore`, `BrowserWebView`, `ui/WebViewScrollBar` |
| System bars | Status/navigation icon contrast for forced light and dark modes | `AppearanceSystemBars.kt` |
| Toppings | Local editor/import plus explicit GitHub catalog discovery; browser runtime and remote state stay controller-owned | `ui/UserscriptManagementScreen.kt`, `ui/ToppingCatalogScreen.kt` |
| App data archive | SAF launch and confirmation stay in the activity; bounded ZIP policy and cold-process restore stay in focused data/transfer owners | `MainActivity.kt`, `data/AppDataArchive*`, `AppDataTransferActivity.kt` |
| Android backup | Encrypted cloud and device-transfer inclusion policy | `res/xml/data_extraction_rules.xml`, [`app-data-archive.md`](app-data-archive.md#android-auto-backup) |
| Candy Recall | Disabled-by-default local readable-page indexing and clear-on-disable behavior | `BrowserSessionStore`, `RecallRepository`, [`recall.md`](recall.md) |

## Choices

| Setting | Values | Default |
| --- | --- | --- |
| Appearance | System, light, dark, AMOLED | System |
| Force dark mode on websites | Off, on | Off |
| Color palette | Material You, Candy, neutral | Material You |
| Surfaces | Clear, frosted | Clear |
| Shape | Angular, rounded, extra rounded | Rounded |
| Startup animation | Off, on | On |
| Candy Recall | Off, on | Off |
| Page translation provider | Google Translate, Yandex Translate, Kagi Translate | Google Translate |

### Surface semantics

| Surface | Browser chrome treatment |
| --- | --- |
| Clear | Opaque neutral containers with standard elevation |
| Frosted | Light translucent chrome with a live blur of browser content behind it |

Frosted exposes three persisted controls while selected:

| Control | Range | Default |
| --- | --- | --- |
| Transparency | 0–80% | 40% |
| Address-bar transparency | 0–80% | 40% |
| Blur strength | 0–100% | 60% |

## Invariants

- Appearance settings are global and persist across normal and private browsing.
- Startup animation is global and enabled by default. Disabling it skips Candy's custom animation
  on a cold launcher start and opens the address editor immediately on cold and warm launcher
  starts. External launches, activity recreation, and first-run onboarding do not force the editor
  open.
- Unknown stored values fall back per field; one corrupt value does not discard valid choices.
- AMOLED keeps root surfaces black. Frosted transparency does not override AMOLED black chrome.
- Frosted changes only Candy browser chrome. It does not inject styles into websites or claim backdrop refraction.
- Frosted uses WebView blur sources while browsing and Compose-backed blur sources on the new-tab page and tab overview.
- General transparency controls menus and other browser chrome; address-bar transparency independently controls the browsing and tab-overview address bars.
- Blur strength is global across frosted address chrome, menus, search suggestions and supported sheets.
- Tab options blur the visible tab-overview cards behind the menu instead of falling back to a sharp translucent surface.
- The main `…` menu shares the active browser-content blur source, including the new-tab page; its rows remain translucent so the effect stays visible.
- Bottom sheets use the general Frosted transparency setting; Privacy X-Ray also blurs the active browser content. Clear and AMOLED sheets remain opaque.
- Forced light, dark and AMOLED modes update system-bar icon contrast independently from system night mode.
- Appearance mode also selects Android's activity night resources. WebView therefore exposes the
  same effective light or dark mode to websites through `prefers-color-scheme`; AMOLED is dark,
  while System follows the device setting. Live changes reload active pages against the new Android
  theme without replacing the browser controller or persisting its in-memory private tabs. An open
  Link Peek closes before its ephemeral preview WebView is released.
- WebView algorithmic darkening is off by default. The optional **Force dark mode on websites**
  setting allows WebView to recolor sites without their own dark theme while the effective app
  appearance is dark. Websites can still respond to `prefers-color-scheme`; forced darkening may
  cause display issues by altering author-defined colors and image assets.
- Shape tokens affect browser chrome and controls; geometry owned by gesture or transition rules stays unchanged.
- Each top-level settings destination has a distinct leading icon on the settings home page.
- Candy Recall is an explicit opt-in under Protection & data. Its summary states that readable text
  from regular pages is stored locally for search and private tabs are never included. Turning it
  off clears stored Recall text; ordinary History remains governed by its own settings.
- Tabs & gestures owns the expanded address-bar action editor. The former standalone tab-button
  visibility switch is intentionally absent because **Tabs** is now an ordinary configurable action.
- The Browser setting for the draggable page scroll bar is global and defaults off. When enabled,
  native WebView scroll bars are replaced by a touch-sized thumb that appears during scrolling,
  supports direct dragging, and fades after interaction.
- Page translation provider is global and persists across regular and private browsing. Translation
  itself remains an explicit page action; no source URL or translated content is stored separately.
