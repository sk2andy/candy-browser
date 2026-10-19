# Settings option map — issue 320

Inventory captured before presentation edits at `b0cc027f4e33b09c714fb09fca4bb2c4644b6fe4`.
The final review branch is based on `f74efd01328062f827eec8119eaba69f9ba729c0`, including the hidden-keyboard inset fix.
Its #323 runtime, manifest, store and browser rules are unchanged from that merge. The previously
reviewed service guard is retained: pointer-up clears multi-touch tracking, and pointer index 1 is
read only while at least two pointers exist. Live multi-touch verification is still outstanding.
Existing destinations, callbacks, enum choices, numeric bounds, preference keys and defaults remain authoritative. The #320 reorganization preserves their behavior. The separately authorized #323 feature is already part of the current main baseline. All 101 current preference keys remain unchanged; there is no store migration.

## Destination and dependency map

| Existing location | Connected destination / groups | Options and dependencies retained |
| --- | --- | --- |
| Home | Browsing; Tabs; Gestures; Theme & interface; Extensions; Privacy & data; About | Every existing navigation entry; Firefox extensions only with engine callback; developer options only after existing unlock; About long press retains unlock |
| Search | Search engine; Suggestions | Every SearchEngine enum entry, AI logo (only engines supporting AI), SearXNG instance URL (SearXNG engine or suggestions); local history suggestions; every suggestion provider; fallback excludes SearXNG and is disabled unless SearXNG suggestions selected |
| Theme & interface | Animations; **Colors**, **Surfaces and shapes**, **Address bar style**, **Web pages** detail pages | System/light/dark/AMOLED, animations, force dark websites (System WebView only), font 50–200%/5%; palette; address color including custom RGB and reset; surface clear/frosted and all three dependent transparency/blur sliders; shape; address load style; address bar style. Core iOS capabilities remain narrower |
| App icon | Icon selection | All 12 launcher aliases, selected mark and existing preview; Android component persistence |
| Browser behavior | Browser setup; **Startup**, **Favorites**, **Web pages**, **Links & apps** detail pages | App language when locale list exists; engine omitted in SystemWebView-only build; default browser; startup animation, startup address focus, home startup; favorite animation + dependent speed, bookmark HTML import; device search, link routing, immersive mode, scrollbar, translation; app link handling, external preview + dependent profile |
| Tabs | Overview; Tab lifecycle; Profile options | Overview Hero/Grid/List + list origin disabled for Hero; stack folder Hero/Grid/List on its own preview page; recent sorting; undo closed tab on its own feature page; resident limit; auto-close lifetime; profiles + Guest Bubble dependent on profiles and platform availability |
| Gestures | Link interactions; Address bar buttons; Gestures | Address long press; separate link long-press and Link Peek swipe selection pages (swipe entry visible disabled outside Link Peek); Link Peek buttons; address buttons; menu actions; parked pill docking; swipe mode; dismissal resistance |
| Gestures → Address long press | Existing semantic action sections | Every AddressBarLongPressAction enum option; radio selection remains mutually exclusive |
| Gestures → Address bar buttons | Existing visible/hidden action editor groups | Every AddressBarAction, order, visibility and drag behavior preserved |
| Gestures → Link Peek buttons | Existing visible/hidden action editor groups | Every LinkPeekAction, order, visibility and drag behavior preserved |
| Gestures → Menu actions | Presentation; Toolbar; Existing action sections | Floating/BottomSheet; Scroll/Swipe content (dependent on BottomSheet); five toolbar slots and empty choices; all supported BrowserMenuEntry locations, hidden/overflow/switcher membership and reset. Concurrent #323 owns feature changes |
| Player | Playback | Autoplay supported capability; every InlineMediaPlayerMode on an explanatory selection page; backward/forward 5/10/15/20/30/60-second sliders disabled if unsupported or disabled mode |
| Downloads | Download handling | Built-in/ask/external manager; folder and reset disabled outside built-in; 1DM session sharing disabled unless selected/available 1DM; private-mode handoff unchanged |
| Privacy protection | Protection tools; Content blocking; Connections | Permission Radar, X-Ray, Filter Studio; ads/trackers, cookie banners, third-party cookies, DNT, GPC, De-AMP; HTTPS modes + DoH providers/custom endpoint disabled for unsupported engine; WebRTC modes retain engine-specific descriptions; build CA warning |
| History & browser data | History; App data | Save history + dependent clear-on-close; Recall and existing clear-on-disable behavior; export/import ZIP; clear browsing data; private state exclusion unchanged |
| Browser → Link routing | Rule list and editor | Add/edit/delete, enabled, order, domain, subdomains, target kind, target profile/capsule (omitted for Reader); existing limits and target validation |
| Browser → Device search | Master feature; Profile; Bookmarks; Articles | Enable, bookmark profile, selected bookmark/article IDs; busy prevents changes; disabled feature keeps dependent selection visible and disabled |
| Site Capsules | Capsule list | Existing capsule edit/delete, names, host and ownership |
| Toppings | Script list, editor, discover | Import/add/edit/delete, enable, frame scope, source; capability restrictions and catalog discovery/update controls unchanged |
| Sync | Setup; Connection; This device; Encryption; Actions; Devices | Server endpoint, username/password and visibility; local profile, device name/icon/accent; recovery key/password; configure/enroll/refresh; paired profiles/device status; extension links/share and documentation. Existing validation, submission state, secure storage and private exclusions retained |
| Developer options | Security; Diagnostics; Presentations; Experiments; Performance; Memory; Safe area | HTTP autofill capability + confirmation; app logging/export/delete; Gecko logging capability/modules/apply/export/delete/status; process input diagnostics/copy; onboarding/release replay; native fallback; chrome dispatch; idle timeout/background warm count/page cache lifetime; layout quiet/failed checks; CSS correction and all subordinate switches/sliders/reset |
| About & legal | Application information; Legal links | Version/build, developer/imprint, licenses, uBlock source, bundled notices and existing URL actions |

## Menu and shared Core completion

Menu presentation and content layout share one connected group. All five toolbar slots share another;
the content layout and toolbar values remain visible but disabled under Floating, and reselecting
BottomSheet restores their availability without changing stored choices. Existing action-location
sections have connected groups, with allowed locations, available entries, empty choices, normalization
and change-only callbacks retained.

Menu style, bottom-sheet content and tab overview have dedicated destinations on Android and Core. Each uses a
theme-aware Candy vector preview above a connected radio group. Selecting a value updates the existing
callback immediately and stays on the page; Back does not change the value.

| Selection | Destination | Parent | Preserved choices |
| --- | --- | --- | --- |
| Menu style | `MenuStyle` | Menu actions | Floating / Bottom sheet |
| Bottom-sheet sections | `MenuContentLayout` | Menu actions | Vertical scroll / Swipe pages; visible disabled under Floating, including direct search access |
| Tab overview | `TabOverview` | Tabs (Core: Tabs & Gestures) | Hero / Grid / List; list origin remains on parent and retains its existing dependency |

Existing search IDs lead directly to these pages, with the same return-to-search behavior and query
retention. Header and Android system/predictive Back use the owning destination. The graphics are
decorative, generated by Compose Canvas and use Candy theme colors; they are not copies of Android
screenshots. Previews are capped in width on large displays, and the whole page scrolls at large font
scales. Radio rows expose one selected/disabled accessibility node each. Selected and unselected radio rows use the same neutral `surfaceContainerLowest` background;
selection is conveyed by the `primary` radio indicator and selected accessibility state. No filled
selection surface is used. Pixel regression tests compare both backgrounds and require at least 3:1
indicator contrast for the captured dynamic-light fixture, Candy/Neutral light and dark palettes, and
device dynamic light/dark palettes. Disabled controls retain their disabled colors and opacity.

Smaller choices continue using shared `SettingsDropdown` and `SettingsDropdownItem`: a floating,
scrollable `surfaceContainerLow` surface, optional leading icon, and trailing radio indicator. Selected
and unselected rows share the menu surface. Toolbar choices keep the empty option first, followed by
existing action sections separated by dividers; filtering and slot assignment are unchanged. The same
presentation covers App Icon (real icon preview), HTTPS-only (supporting descriptions), and Sync
profile/device selectors. Sync profile accessibility labels retain the identifying emoji. No nested
menu hierarchy is added to direct value selections.

The menu treatment follows the vertical-menu-with-divider example supplied for
[Material menus](https://m3.material.io/components/menus/specs), the
[Compose menu guide](https://developer.android.com/develop/ui/compose/components/menu), and the
official AndroidX `StandardMenuTokens`/`MenuDefaults` source. It uses the project's existing Material
version rather than changing dependencies. Standard surface/selection colors come from Candy's
theme (`surfaceContainerLow`, `surfaceContainerLowest`, `primary`, `onSurface`), with flexible text height
and 48dp minimum touch targets. The popup retains Material's focus, scrolling and dismissal behavior.

### Expanded explanatory pages and neutral selection

The earlier approved colored selection revision is saved locally as
`3ef9336e721ed484e83418536ec28c614760b658`. The expanded neutral design retains that checkpoint for comparison. Its Android routes are:

| Selection / feature | Destination | Back parent | Retained controls |
| --- | --- | --- | --- |
| Colors | `AppearanceColors` | Appearance | System/light/dark/AMOLED and all palettes; Core exposes only existing System/light/dark capability |
| Surfaces and shapes | `AppearanceSurfaces` | Appearance | All three shapes; Clear/Frosted; transparency, address transparency and blur stay in one group, visible disabled under Clear |
| Address bar | `AppearanceAddressBar` | Appearance | Every color preset, custom RGB editor/reset, Rainbow/Tonal load style, Classic/Segmented layout |
| Stack folder | `TabStackFolder` | Tabs | Hero/Grid/List with live layout preview |
| Closed-tab undo | `ClosedTabUndo` | Tabs | Existing master switch, illustrated close/undo result |
| Link long press | `LinkLongPress` | Gestures | All eight actions grouped into preview, normal tabs, private tabs and link actions; foreground/background labels retained |
| Link Peek swipe | `LinkPeekSwipe` | Gestures | All nine actions; disabled without Link Peek; private option explicitly foreground |
| Candy Player mode | `PlayerMode` | Player | All five modes with individual explanations; existing engine capability; seek sliders remain on Player |
| External link preview | `ExternalLinkPreview` | Browser links | Existing master and retained dependent profile, including unavailable-profile fallback |
| Device search | Existing Activity | Existing Android Back | Functional search/result diagram; existing master, busy guards and profile/item controls |

The shared navigation hierarchy is also used by Android header/system/predictive Back. The Core host
keeps its legacy combined Tabs & gestures parent and motion, and exposes only supported appearance
subpages. Search identifiers are unchanged; results target the owning child page and preserve query
and return-to-search behavior. Illustrations are decorative and excluded from screenreader traversal;
controls retain selected/disabled roles and labels. New explanations have resource entries for every existing app locale, including simplified and
traditional Chinese. Existing option translations are retained.

`ExpandedSettingsInstrumentedTest` covers selection/reselection of every gesture, stack and player
choice, disabled writes, explanations and 320dp/200% text. Existing appearance/browser/player/tab tests
now exercise the new destinations. Shared Core and JVM navigation/search tests cover new parents and
capabilities. Final verification on the exclusively authorized emulator-5832 passes all 193
Settings and integrated inset tests, including neutral pixel checks, 320dp/200% text, 600dp layout,
and the full header/system-Back route matrix. Full/FOSS/shared JVM suites pass 4,459 tests.
The six existing SettingsSearchInstrumentedTest cases remain excluded because their setup grants
a notification permission; search is checked manually without permission changes. Historical
172/12-test results and colored screenshots belong to their earlier revisions. No TalkBack or
Accessibility service was activated.

Shared Core groups tab overview/list origin/sorting, menu navigation/dismissal resistance, browser
startup and website options. Unsupported platform controls remain disabled. Shared Search groups its
engine and relevant SearXNG URL; its validation and conditional relevance remain unchanged. Each shared
Topping has one named master switch and connected management controls. Frame scope remains visible,
disabled while the Topping is off, and retains its value; Edit/Delete remain available.

The final #323 patch is integrated with its runtime, service, manifest, store, menu availability
filters and preview/save callbacks. Its status-bar group belongs to Gestures. The dependent width
stays visible disabled while its master is off. The exact source hash and added options are below;
combined validation is recorded separately from the originating patch's historical checks.

## Preservation contract

The #320 layout work preserves baseline model, store, controller, runtime, preference keys, defaults and enum values. The authorized #323 extension adds its own feature state and menu action without changing those baseline contracts. The lists below are a reproducible baseline of setting labels and option source owners, including submenu editors. Dynamic options come from the same existing enums/lists rather than copied values. Existing `SettingsDestination` values and search targets remain valid. Android/system/predictive Back must return to the same parent. Group layout must not merge row semantics. Disabled dependents must not emit writes. System Reduce Motion and the global animations preference remain authoritative.

## Baseline source inventory

### AboutLegalSection.kt

Source: `app/src/main/java/dev/sk2andy/materialbrowser/ui/AboutLegalSection.kt`

- Developer & imprint
- Open-source & third-party licenses
- uBlock Origin block source
- Bundled third-party notices

### AddressBarLongPressSettingsPage.kt

Source: `app/src/main/java/dev/sk2andy/materialbrowser/ui/AddressBarLongPressSettingsPage.kt`

- Long-press address bar action
- Dynamic choice owners: `AddressBarLongPressActionSection`, `AddressBarLongPressAction`

### AppIconSettings.kt

Source: `app/src/main/java/dev/sk2andy/materialbrowser/ui/AppIconSettings.kt`

- App icon
- Dynamic choice owners: `AppIconOption`

### AppLanguageSettings.kt

Source: `app/src/main/java/dev/sk2andy/materialbrowser/ui/AppLanguageSettings.kt`

- App language

### AppearanceSettingsPage.kt

Source: `app/src/main/java/dev/sk2andy/materialbrowser/ui/AppearanceSettingsPage.kt`

- Theme & interface
- Dynamic choice owners: `BrowserAppearanceMode`, `BrowserColorPalette`, `BrowserAddressBarColorPreset`, `BrowserSurfaceStyle`, `BrowserShapeStyle`, `BrowserAddressLoadStyle`, `BrowserAddressBarStyle`

### BrowserMenuSettingsPage.kt

Source: `app/src/main/java/dev/sk2andy/materialbrowser/ui/BrowserMenuSettingsPage.kt`

- Dynamic choice owners: `BrowserMenuEntry`

### BrowserSettingsPage.kt

Source: `app/src/main/java/dev/sk2andy/materialbrowser/ui/BrowserSettingsPage.kt`

- Browser behavior
- Web engine
- Startup animation
- Focus address bar on launch
- Open home page on startup
- Favorite animations
- Animation speed
- Import bookmark HTML
- Device search
- Personal link rules
- Full immersive mode
- Draggable scroll bar
- Page translation provider
- Open links in apps
- Preview links from other apps
- Default profile for previews
- Dynamic choice owners: `AndroidBrowserEngineKind`, `StartupAddressFocusMode`, `FavoriteAnimationSpeed`, `PageTranslationProvider`, `ExternalAppLinkHandling`

### DeveloperOptionsSettingsPage.kt

Source: `app/src/main/java/dev/sk2andy/materialbrowser/ui/DeveloperOptionsSettingsPage.kt`

- Developer options
- Password manager on HTTP sites
- App logging
- Export app logs
- Delete app logs
- Gecko logging
- Export Gecko logs
- Delete Gecko logs
- Touch and input diagnostics
- Copy diagnostic status
- Show gesture onboarding
- Show What’s New
- Force native safe-area fallback
- Browser chrome scroll updates
- Layout quiet period
- Required failed checks
- CSS safe-area correction
- Add inset to negative top values
- Check added elements
- Check changed elements
- Require interaction for updates
- Check after resizing
- Interaction window
- Mutation delay
- Elements per batch
- Batch time budget
- DOM classification limit
- Unload idle tabs
- Warm tabs in the background
- Page cache lifetime
- Logging modules
- Dynamic choice owners: `BrowserChromeScrollDispatchMode`

### DeviceSearchSettingsPage.kt

Source: `app/src/main/java/dev/sk2andy/materialbrowser/ui/DeviceSearchSettingsPage.kt`

- Device search
- Share with device search
- Dynamic choice owners: `DeviceSearchKind`

### DownloadSettingsPage.kt

Source: `app/src/main/java/dev/sk2andy/materialbrowser/ui/DownloadSettingsPage.kt`

- Downloads
- Download manager
- Download folder
- Share session data with 1DM
- Subfolder

### GuestBubbleSettings.kt

Source: `app/src/main/java/dev/sk2andy/materialbrowser/ui/GuestBubbleSettings.kt`

- Guest Bubble

### LinkRoutingSettingsPage.kt

Source: `app/src/main/java/dev/sk2andy/materialbrowser/ui/LinkRoutingSettingsPage.kt`

- Personal link rules
- Include subdomains
- Open with
- Destination
- Domain without https://
- Dynamic choice owners: `LinkRoutingTargetKind`

### PlayerSettingsPage.kt

Source: `app/src/main/java/dev/sk2andy/materialbrowser/ui/PlayerSettingsPage.kt`

- Player
- Prevent video autoplay
- Candy Player (experimental)
- Double-tap backward
- Double-tap forward
- Dynamic choice owners: `InlineMediaPlayerMode`

### ProtectionSettingsPage.kt

Source: `app/src/main/java/dev/sk2andy/materialbrowser/ui/ProtectionSettingsPage.kt`

- Block ads & trackers
- Hide cookie banners
- Block third-party cookies
- Do Not Track
- Global Privacy Control
- Auto De-AMP
- HTTPS Only
- Encrypted DNS (DoH)
- WebRTC connections
- Save history
- Clear when Candy closes
- Candy Recall
- Export all app data
- Import app data
- Resolver endpoint
- Dynamic choice owners: `DnsOverHttpsProvider`, `WebRtcProtectionMode`

### SearchSettingsPage.kt

Source: `app/src/main/java/dev/sk2andy/materialbrowser/ui/SearchSettingsPage.kt`

- Search
- Search engine
- Show AI Mode logo
- Show browsing history suggestions
- Search suggestions
- Suggestion fallback
- SearXNG instance URL
- Dynamic choice owners: `SearchEngine`, `SearchSuggestionProvider`

### SettingsScreen.kt

Source: `app/src/main/java/dev/sk2andy/materialbrowser/ui/SettingsScreen.kt`

- Address bar buttons
- Link Peek buttons
- App icon
- About & legal
- Dynamic choice owners: `AddressBarAction`, `LinkPeekAction`

### SettingsSearchCatalog.kt

Source: `app/src/main/java/dev/sk2andy/materialbrowser/ui/SettingsSearchCatalog.kt`

- Dynamic choice owners: `SearchEngine`, `SearchSuggestionProvider`, `TabOverviewMode`, `InactiveTabLifetime`, `BrowserAppearanceMode`, `BrowserColorPalette`, `BrowserAddressBarColorPreset`, `BrowserSurfaceStyle`, `BrowserShapeStyle`, `BrowserAddressLoadStyle`, `BrowserAddressBarStyle`

### SiteCapsulesSettingsPage.kt

Source: `app/src/main/java/dev/sk2andy/materialbrowser/ui/SiteCapsulesSettingsPage.kt`

- Site Capsules

### SyncSettingsPage.kt

Source: `app/src/main/java/dev/sk2andy/materialbrowser/ui/SyncSettingsPage.kt`

- Sync
- Chrome Web Store
- Firefox Add-ons
- Start your sync server
- Install the browser extension
- Create the encrypted workspace
- Connect this device
- Server endpoint
- Username
- Device name

### TabSettingsPage.kt

Source: `app/src/main/java/dev/sk2andy/materialbrowser/ui/TabSettingsPage.kt`

- Stack folder layout
- Sort tabs by recent use
- Undo closed tab
- Automatically close tabs
- Enable profiles
- Long-press address bar action
- Long-press link action
- Link Peek right-swipe action
- Link Peek buttons
- Address bar buttons
- Menu actions
- Move the parked address pill
- Switch address pill with swipes
- Tab dismissal resistance
- Dynamic choice owners: `TabOverviewMode`, `InactiveTabLifetime`, `LinkLongPressAction`, `LinkPeekSwipeAction`

### UserscriptManagementScreen.kt

Source: `app/src/main/java/dev/sk2andy/materialbrowser/ui/UserscriptManagementScreen.kt`

- Script source

### AppearanceSettingsPage.kt

Source: `shared/src/commonMain/kotlin/dev/sk2andy/materialbrowser/shared/ui/settings/AppearanceSettingsPage.kt`

- `title`
- `appearanceMode`
- `animations`
- `forceDarkWebsites`
- `webContentFontSize`
- `colorPalette`
- `addressBarColor`
- `surfaceStyle`
- `frostedTransparency`
- `frostedAddressBarTransparency`
- `frostedBlur`
- `shapeStyle`
- `addressLoadStyle`
- `addressBarStyle`
- Dynamic choice owners: `BrowserAppearanceMode`, `BrowserColorPalette`, `BrowserAddressBarColorPreset`, `BrowserSurfaceStyle`, `BrowserShapeStyle`, `BrowserAddressLoadStyle`, `BrowserAddressBarStyle`

### BrowserMenuPresentationSettings.kt

Source: `shared/src/commonMain/kotlin/dev/sk2andy/materialbrowser/shared/ui/settings/BrowserMenuPresentationSettings.kt`

- Dynamic choice owners: `BrowserMenuPresentation`, `BrowserMenuBottomSheetContentLayout`

### BrowserMenuSettingsPage.kt

Source: `shared/src/commonMain/kotlin/dev/sk2andy/materialbrowser/shared/ui/settings/BrowserMenuSettingsPage.kt`

- Dynamic choice owners: `BrowserMenuEntry`, `BrowserMenuConfigurationSection`

### CandyCoreSettingsPages.kt

Source: `shared/src/commonMain/kotlin/dev/sk2andy/materialbrowser/shared/ui/settings/CandyCoreSettingsPages.kt`

- Dynamic choice owners: `PageTranslationProvider`

### CandySearchSettingsPage.kt

Source: `shared/src/commonMain/kotlin/dev/sk2andy/materialbrowser/shared/ui/settings/CandySearchSettingsPage.kt`

- Dynamic choice owners: `SearchEngine`

### CoreSettingsSections.kt

Source: `shared/src/commonMain/kotlin/dev/sk2andy/materialbrowser/shared/ui/settings/CoreSettingsSections.kt`

- `overviewMode`
- `listStartsAtBottom`
- `title`
- Dynamic choice owners: `TabOverviewMode`, `PageTranslationProvider`


## Implemented hierarchy and validation contract

| Detail route | Parent | Existing options moved into it |
| --- | --- | --- |
| `AppearanceSurfaces` | `Appearance` | Surface style; frosted transparency; address transparency; blur |
| `AppearanceAddressBar` | `Appearance` | Color preset/custom/reset; loading indicator; address style |
| `AppearanceWebsites` | `Appearance` | Force dark websites; website font size |
| `BrowserStartup` | `Browser` | Startup animation; address focus; open home |
| `BrowserFavorites` | `Browser` | Favorite animations; dependent speed; bookmark HTML import |
| `BrowserWebsites` | `Browser` | Device search; Link routing; immersive mode; scrollbar; translation |
| `BrowserLinks` | `Browser` | App link handling; external preview; dependent preview profile |

`SettingsNavigationRules` is the shared hierarchy for header/predictive Back and transition direction.
The legacy combined Tabs & gestures route remains supported. Search keeps all existing catalog IDs,
remaps moved options to their actual detail route, and overrides that route's Back parent with Home
while its result is active, restoring the query. No new search options are invented by this change.

Connected groups use the theme’s extra-large exterior and extra-small row corners (28 dp / 4 dp
with the default Rounded shape), with 2 dp separators. Angular and ExtraRounded retain their
existing shape tokens;
ordinary switches, choices, fields and sliders share the group surface. Page and row colors are
Material semantic neutral colors, so dark and AMOLED themes remain supported. Group semantics do not
merge neighboring controls: each switch exposes its own label, checked state and disabled state.
The Device Search master alone uses a large pill. Its decorative search illustration performs a
single scale transition when enabled changes and uses an immediate transition with reduced motion.
It never starts a repeating animation or announces decorative artwork.

The two new section labels (Tab lifecycle and Link interactions) have resources for all 29 declared
app languages. All other headings reuse existing localized resources.

Validation targets: `SettingsGroupsInstrumentedTest`, `SettingsSearchInstrumentedTest`,
`SettingsBackRouterInstrumentedTest`, `AppearanceSettingsScreenInstrumentedTest`,
`BrowserSettingsScreenInstrumentedTest`, `TabSettingsScreenInstrumentedTest`,
`PlayerSettingsPageInstrumentedTest`, `SearchSettingsScreenInstrumentedTest`,
`DownloadSettingsPageInstrumentedTest`, `DeveloperOptionsSettingsPageInstrumentedTest`,
`DeviceSearchSettingsPageInstrumentedTest`, `SyncSettingsPageInstrumentedTest`,
`SettingsBackRulesTest`, `SettingsSearchCatalogTest`, and shared `SettingsNavigationRulesTest`.
Final Android verification uses the exclusively authorized emulator-5832 and isolated package
`dev.sk2andy.materialbrowser.settingsredesign320`; execution evidence is recorded separately.

## Combined status-bar settings (#323)

The authoritative #323 patch (SHA-256 `1135e69f695f5fc8307adefaa2af6ffa2b4240529cdff094d35c8b47f76a276b`) adds the following options without changing existing setting keys or defaults:

| Setting | Destination/group | Dependency and retained state |
| --- | --- | --- |
| Status-bar tap to scroll to top | Gestures / status-bar group | Global enabled setting; enabling shows the existing accessibility explanation. Platform service remains Android-only. |
| Status-bar target width | Same group | Existing bounded percent range/default from `StatusBarScrollRules`; always visible, disabled while global feature is off. Saved width retained, no preview/save writes while disabled. Drag previews; release saves and signals completion. |
| Status-bar menu action location | Menu actions / Page | Available only while feature enabled; saved location is retained when hidden. Existing Android Firefox-extension exclusion is unchanged. |

Both title and width are discoverable through Settings search with stable resource-based identifiers under Gestures. The width slider exposes its localized title and displayed percentage to accessibility services. The runtime/service/store/manifest wiring comes from the supplied #323 patch; #320 grouping, navigation, search return behavior, five retained toolbar slots and shared Core restrictions remain intact.
