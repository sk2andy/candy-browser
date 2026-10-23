# Appearance and settings

The complete option inventory, old-to-new grouping, dependent controls and capability boundaries are
recorded in [Settings option map](settings-option-map.md).

## Android predictive Back

| Surface | Behavior | Owner |
| --- | --- | --- |
| Settings home | Existing shrink preview reveals the browser; a nonempty settings search clears first | `BrowserScreen`, `BrowserSettingsOverlay` |
| Settings subpages | Only the current page shrinks; its parent settings page fills the background throughout the gesture. Cancellation restores the page, commit returns to its parent. | `SettingsBackRouter`, shared `SettingsRouter`, `NativeScreenBackHandler`, `SettingsBackRules` |
| Nested settings | Theme details return to Theme & interface; browser details return to Browser behavior; Link routing returns to Web pages; action editors return to Gestures; Topping catalog returns to Toppings. An active search result returns directly to the preserved Settings search. | shared `SettingsNavigationRules`, `SettingsBackRules`, `SettingsScreen` |
| Snoozed tabs | Default shrink, followed by the existing dismissal fade; rescheduling dialogs retain Back priority | `SnoozedTabsScreen`, `NativeScreenBackHandler` |

The shared motion keeps the existing 0.9 minimum scale, edge-directed translation and rounded
corners. A canceled gesture restores the surface without navigation. Button/three-button Back
continues to navigate without waiting for a gesture animation.

`SettingsBackRouterInstrumentedTest` checks the visible parent preview, opaque foreground,
gesture cancellation and single-step commits through nested settings destinations.

## Ownership

| Layer | Responsibility | Main code |
| --- | --- | --- |
| Model | Stable, persisted appearance choices and safe fallback values | `shared/src/commonMain/.../data/AppearanceSettings.kt` |
| Persistence | Global appearance preference round trips | `data/BrowserSessionStore.kt` |
| State | Observable selection and update wiring | `browser/BrowserController.kt` |
| Theme | Platform design language, color schemes, motion, Android night resources, root/system-bar wiring, website color-scheme and font-size preferences, surface treatment, shape tokens and AMOLED surfaces | `MainActivity.kt`, `AppearanceNightMode.kt`, `browser/BrowserController.kt`, `browser/gecko/GeckoRuntimeOwner.kt`, `ui/theme/CandyDesignSystem.kt`, `ui/theme/MaterialBrowserTheme.kt` |
| Settings routing | Shared destination model, transition, home, controls and core pages; platform resources, icons, effects and persisted state stay adapters | `shared/src/commonMain/.../SettingsDestination.kt`, `shared/src/commonMain/.../ui/settings`, Android `ui/SettingsScreen.kt` adapters |
| Android settings organization | Settings Home separates Browsing, Tabs, Gestures, Theme & interface, Extensions, Privacy & data, and About. Tabs and Gestures have distinct subpages; App icon and History & browser data have their own routes. Search catalog entries target the same pages. Related options use connected rounded groups, and dependent rows remain visible but disabled while their parent choice is inactive. | `ui/SettingsHomePage.kt`, `ui/SettingsSearchCatalog.kt`, `ui/SettingsScreen.kt`, `shared/ui/settings/SettingsComponents.kt` |
| Android settings search | Memory-only search on Settings Home matches localized option titles, descriptions and destination context. Capability-filtered results open existing routes and highlight/scroll common controls into view. | `ui/SettingsSearchRules.kt`, `ui/SettingsSearchCatalog.kt`, `ui/SettingsSearchField.kt`, shared `SettingsSearchTarget.kt` |
| Android app language | Settings → Browser → App language uses native language names and a Follow device language option. Android's `LocaleManager` owns persistence and synchronization with system app-language settings. The supported list comes from `res/xml/locales_config.xml`; all supported languages and localization checks are listed in [`app-languages.md`](app-languages.md). | `data/AppLanguagePreferences.kt`, `ui/AppLanguageSettings.kt`, `ui/BrowserSettingsOverlay.kt`, `res/values-*/strings.xml` |
| Android app icon | Settings → App icon opens a scrollable list with an icon preview, localized name and radio button for each option. Tapping anywhere on a row switches one launcher alias at a time. Android's enabled component state persists the choice; MainActivity keeps external link and share filters. The artwork is recomposed from the user supplied Candy Browser icon overview for a centered, circular launcher mask. | `data/AppIconSelection.kt`, `ui/AppIconSettings.kt`, `AndroidManifest.xml`, `res/drawable-nodpi/ic_launcher_*_art.png` |
| Language changes | MainActivity handles locale and layout-direction configuration updates without recreation, retaining open regular/private tabs and the current settings page. Localized Android resources refresh through the existing configuration dispatch; device-language changes apply when no app override is selected. | `MainActivity.kt`, `AndroidManifest.xml`, `AppLanguageSettingsInstrumentedTest`, `AppLanguagePreferencesInstrumentedTest` |
| Player settings | Direct Settings → Player page for video autoplay, Candy Player mode and independent seek sliders; header and Android Back return to Settings Home. Browser no longer duplicates these controls; shared/iOS keeps the unavailable route disabled. | `ui/PlayerSettingsPage.kt`, `ui/InlineMediaPlayerSeekSlider.kt`, `SettingsDestination.Player`, `PlayerSettingsPageInstrumentedTest`, `PlayerSettingsNavigationInstrumentedTest` |
| Appearance UI | Shared production destination and controls; Android supplies live persisted state, iOS shows them disabled until it owns equivalent state | `shared/src/commonMain/.../ui/settings/AppearanceSettingsPage.kt`, Android `ui/AppearanceSettingsPage.kt` adapter |
| Browser menu presentation | Android **Gestures → Address bar buttons → Menu actions** chooses Floating menu or Bottom sheet and Scroll or two Swipe pages; five lower-toolbar positions accept all supported individual tab-menu actions or empty slots; global stable preferences retain placement and slot order. | `BrowserMenuLayout`, `BrowserMenuSettingsPage`, `BrowserSessionStore` |
| Address-bar actions | Persisted ordered action layout plus drag-editor navigation under Gestures | `data/AddressBarActionLayout.kt`, `ui/AddressBarActionEditor.kt`, `BrowserSessionStore` |
| Address-pill swipe mode | Persisted opt-in under Gestures; vertical swipes on the page expand downward and compact upward, including pages without scrolling. Disabled mode retains page-scroll behavior. | `BrowserSessionStore`, `BrowserController`, `ui/TabSettingsPage`, `ui/BrowserViewport` |
| Page scroll bar | Persisted opt-in, engine-neutral scroll metrics and draggable auto-hide overlay | `BrowserSessionStore`, browser-engine session ports, `ui/BrowserScrollBar` |
| Developer options | Persisted hidden unlock, bounded safe-area fallback tuning, insecure-HTTP autofill override, process-local input diagnostics, privacy-safe runtime report, [opt-in app logging and export](app-logging.md), and presentation replay actions | `DeveloperSettings`, `BrowserSessionStore`, `AppLogging`, `BrowserInputDiagnostics`, `DeveloperDiagnosticsReport`, `BrowserController`, `MainActivity`, `ui/DeveloperOptionsSettingsPage` |
| System bars | Status/navigation icon contrast for forced light and dark modes | `AppearanceSystemBars.kt` |
| Toppings | Local editor/import plus explicit GitHub catalog discovery; browser runtime and remote state stay controller-owned | `ui/UserscriptManagementScreen.kt`, `ui/ToppingCatalogScreen.kt` |
| App data archive | SAF launch and confirmation stay in the activity and History & browser data page; bounded ZIP policy and cold-process restore stay in focused data/transfer owners | `MainActivity.kt`, `ui/ProtectionSettingsPage.kt`, `data/AppDataArchive*`, `AppDataTransferActivity.kt` |
| Android backup | Encrypted cloud and device-transfer inclusion policy | `res/xml/data_extraction_rules.xml`, [`app-data-archive.md`](app-data-archive.md#android-auto-backup) |
| Candy Recall | Disabled-by-default local readable-page indexing and clear-on-disable behavior | `BrowserSessionStore`, `RecallRepository`, [`recall.md`](recall.md) |

## Choices

| Setting | Values | Default |
| --- | --- | --- |
| Appearance | System, light, dark, AMOLED | System |
| Android app icon | Classic; Outline light/dark; Cookie, Lemon, and Donut in regular and Maxed sizes; Cotton Candy regular and Freeform; Jelly Beans | Classic |
| Animations | Off, on | On |
| Force dark mode on websites | Off, on | Off |
| Website font size | 50–200% in 5% steps | 100% |
| Color palette | Material You, Candy, neutral | Material You |
| Address-bar color | Theme, dimmed, graphite, black, custom RGB hex | Theme |
| Surfaces | Clear, frosted | Clear |
| Shape | Angular, rounded, extra rounded | Rounded |
| Android browser menu | Bottom sheet, Floating menu | Bottom sheet |
| Bottom-sheet sections | Scroll, two Swipe pages (page actions / page controls) | Scroll |
| Address bar style | Classic, segmented | Classic |
| Address-bar loading indicator | Rainbow, tonal | Rainbow |
| Startup animation | Off, on | On |
| Address focus on launch | When startup animation is off, every launch, never | When startup animation is off |
| Open home page on startup | Off, on | Off |
| Long-press link action | Link Peek, copy, share, download, regular foreground/background tab, private foreground/background tab | Link Peek |
| Link Peek right-swipe action | Background tab, foreground tab, new stack with source, private tab, copy, share, favorite, snooze, Reader offline; visible disabled unless Link Peek is selected | Background tab |
| Switch address pill with swipes | Off: follows page scrolling; on: swipe down expands, swipe up compacts | Off |
| Candy Recall | Off, on | Off |
| Page translation provider | Google Translate, Yandex Translate, Kagi Translate | Yandex Translate on Android; Google Translate on iOS |
| Prevent automatic video playback | Off, on | Off |
| Candy Player double-tap backward | Discrete slider: 5, 10, 15, 20, 30 or 60 seconds | 10 seconds |
| Candy Player double-tap forward | Discrete slider: 5, 10, 15, 20, 30 or 60 seconds | 10 seconds |
| Developer safe-area layout quiet | 100–800 ms in 50-ms steps | 400 ms |
| Developer safe-area failed checks | 2–5 | 3 |
| Force native safe-area fallback | Off, on | Off |
| Gecko inset addition for negative CSS `top` | Off, on | Off |
| Touch and input diagnostics | Off, on for current process | Off |

## Cross-platform settings migration

Settings destinations, page shells, controls and core renderers live in `shared/src/commonMain`.
Android opts into distinct Tabs, Gestures, Extensions, App icon and History & browser data entries;
shared-home callers keep the combined Tabs & gestures entry and their existing group defaults. Android's
Browser setup links to Startup, Favorites, Web pages, and Links & apps detail pages. Theme & interface
links to Colors, Surfaces and shapes, Address bar style, and Web pages. Tabs groups overview/layout, lifecycle and profile
controls separately; Gestures groups link interactions, action editors and gesture controls. These are
presentation routes only; setting ownership, stored keys and values do not change. Android resolves localized
resources, drawable icons, frosted container color and persisted state through adapters. The Android
settings organization does not change the behavior or persistence of the shared appearance and tab
settings. Each visible home entry has a distinct icon: gestures use a drawn stroke, the app icon uses
an app grid, Toppings use sparkles, and history uses a clock with a return arrow. Android drawables and
the shared Material fallback keep these motifs aligned.

### Surface semantics

Candy owns one semantic UI component tree. Platform design changes below that tree:

| Layer | Shared owner | Platform responsibility |
| --- | --- | --- |
| Browser component | Address state, actions, layout slots, accessibility | None |
| `CandyTheme` | Design-language selection and appearance settings | Root selects Material Expressive or Liquid Glass |
| Motion scheme | Transition meaning and named motion tokens | Design language supplies spring and fade values |
| `CandyChromeSurface` | Semantic chrome boundary and content slot | Renderer draws Android Material/frosted chrome or iOS Liquid Glass |

`MaterialBrowserTheme` remains a compatibility wrapper for Android tests and callers. New app roots use
`CandyTheme`. Components must not branch on the operating system; they read design and motion tokens or
delegate to `CandyChromeSurfaceRenderer`.

The current repository contains only the Android target. This change is the preparatory Compose
Multiplatform seam; its renderer remains Material 3 plus `BlurView`. The Liquid Glass token set marks
surfaces with the semantic `CandyChromeTreatment.PlatformNative`. After the KMP source sets exist, iOS must
provide a UIKit-backed renderer behind the same Compose surface contract. Address-bar state and layout do
not get copied.

| Surface | Browser chrome treatment |
| --- | --- |
| Clear | Opaque neutral containers with standard elevation |
| Frosted | Light translucent glass chrome; live website blur depends on engine and Android version |

Frosted keeps three persisted controls together in its surface group. They remain visible and disabled under Clear:

| Control | Range | Default |
| --- | --- | --- |
| Transparency | 0–80% | 40% |
| Address-bar transparency | 0–80% | 40% |
| Blur strength | 0–100% | 60% |

## Invariants

- Appearance settings are global and persist across normal and private browsing.
- The Android app icon is a global launcher component choice. Exactly one of the twelve selectable
  launcher aliases is enabled after a selection, while MainActivity remains enabled for external intents.
  The six square PNGs are based on `Candy Browser Icon-Übersicht.png`.
  Regular artwork retains room around the motif. Maxed Cookie
  and Donut fill the circular Pixel Launcher mask; other launcher shapes may crop
  their edges. The Cotton Candy stick stays within Android's
  [66 dp safe zone](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive).
  The illustrated adaptive icons have a full pastel background layer, including outside their
  inset artwork. Regular Lemon and Maxed Lemon omit the leaf; Maxed Lemon fills
  the circular mask. Freeform Cotton Candy shows a complete stick; regular
  Cotton Candy keeps a compact round puff. Jelly Beans stay centered.
- Outline light and dark preserve Classic's three separate curved leaves. Only these two icons
  use a proportional 16⅔% foreground inset on each side (18 dp on a 108 dp foreground layer).
  This gives the outlines more breathing room and scales with small previews; Classic and
  the other illustrated icons retain their existing insets.
  They use unfilled black contours on white / white contours on black, with a full adaptive
  background and an outline-only monochrome layer for themed launchers. The original shared
  `ic_launcher_foreground_art.png` remains unchanged. Regenerate the vector paths with
  [`scripts/generate_outline_app_icons.py`](../../scripts/generate_outline_app_icons.py), which
  pins the source SHA-256 and smooths raster stair steps into cubic contours. Sampled curves
  stay within 2.5 source pixels (0.63 dp before the foreground inset) of the alpha boundary.
- The transparent Cotton Candy source cutout is kept at
  [`images/app-icons/cotton-candy-freeform-transparent.png`](images/app-icons/cotton-candy-freeform-transparent.png).
  On the tested Pixel Launcher, a transparent adaptive background rendered black and a legacy
  transparent PNG received a white circular backing, so the selectable launcher icon retains
  its pastel background.
- Animations are global and enabled by default. Disabling them supplies a zero
  `MotionDurationScale` to each Android Compose root, skips Candy's startup and favorite-launch
  animations, and removes app-owned Activity window transitions. The setting applies live to the
  main browser; standalone Candy activities read it when they open. Gesture-driven position changes
  remain direct manipulation rather than timed animation.
- Startup animation is global and enabled by default. Disabling it skips Candy's custom animation
  on a cold launcher start. Address focus on launch is global and preserves the previous behavior
  by default: cold and warm launcher starts open the address editor only while the startup animation
  is disabled. Users can instead focus it on every launcher start (after the animation on cold
  starts) or never focus it automatically. External launches, activity recreation, first-run
  onboarding, and release notes do not force the editor open. Explicit address-bar taps, hardware
  focus actions, and new-tab actions still focus the editor in every mode.
- Open home page on startup is global and disabled by default. When enabled, normal cold and warm
  launcher opens select a fresh blank tab while keeping restored tabs, except when address focus
  on launch is set to Never: that choice preserves the last selected tab and keeps the keyboard
  closed. An existing fresh regular
  blank tab in the active profile is reused. External links, launcher shortcuts, Site Capsules, and
  activity recreation keep their own destinations.
- Unknown stored values fall back per field; one corrupt value does not discard valid choices.
- Address-bar colors override only address chrome. Theme preserves the selected Material You, Candy,
  or neutral surface roles; Reset selects Theme and removes the stored custom color. Custom colors
  accept `#RGB` or `#RRGGBB`, normalize to uppercase `#RRGGBB`, and fall back to Theme when invalid.
  Presets derive a distinct inner-field tone plus black-or-white content and accent roles. Frosted
  applies its existing transparency and blur after resolving the opaque preset color. For an
  override, it raises the tint opacity only as far as needed to keep text contrast safe against
  either a light or dark website backdrop. AMOLED
  keeps the selected address color opaque and disables blur.
- Address-bar style is global. Segmented groups expanded actions and the address field into one
  primary pill, keeps the fixed trailing action in a separate pill, and replaces that action with
  editor dismissal while focused. The focused input stays borderless. Compact, parked, overview,
  command-feedback, external-preview and find-in-page chrome retain their existing geometry.
- AMOLED keeps root surfaces black. An explicit address-bar color may color address chrome, but it
  stays opaque; Frosted transparency and blur do not override AMOLED chrome.
- Frosted changes only Candy browser chrome. It does not inject styles into websites or claim backdrop refraction.
- Frosted uses view-hierarchy capture for Android System WebView on Android 13 and newer,
  native `SurfaceView` blur regions for GeckoView on Android 17 and newer, and Compose-backed blur
  sources on the new-tab page and tab overview. GeckoView on Android 13, 14, 15 and 16 keeps its
  performant `SurfaceView` and renders the glass treatment without website blur.
- The status-bar protection is a static surface-tint fade drawn above page content. It never samples
  or continuously invalidates the browser engine; the optional Frosted address chrome keeps its
  separate live blur source.
- General transparency controls menus and other browser chrome; address-bar transparency independently controls the browsing and tab-overview address bars.
- Blur strength is global across frosted address chrome, menus, search suggestions and supported sheets.
- Tab options blur the visible tab-overview cards behind the menu instead of falling back to a sharp translucent surface.
- The main `…` menu shares the active browser-content blur source, including the new-tab page; its rows remain translucent and its individual quick-action tiles use the configured blur strength. It opens from the address-bar action with a spring scale-and-rise transition and leaves with a short fade-and-shrink transition.
- Bottom sheets use the general Frosted transparency setting; Privacy X-Ray also blurs the active browser content. Clear and AMOLED sheets remain opaque.
- Forced light, dark and AMOLED modes update system-bar icon contrast independently from system night mode.
- Dark new-tab and address-editor backgrounds, the Candy logo backing, private-mode backing and
  tab-switch suggestion containers use dark brand or neutral Material surface roles. Dynamic accent and inverse
  roles can be light even in a dark palette, so they do not own those dark containers. Brand artwork
  retains its original colors; light appearance keeps its existing accent treatment.
- Appearance mode also selects Android's activity night resources. Both browser engines therefore
  expose the same effective light or dark mode to websites through `prefers-color-scheme`; AMOLED is
  dark, while System follows the device setting. Candy resolves the persisted System choice from
  effective Activity resources and forwards a concrete light or dark website preference, avoiding
  dependence on Gecko's separate native night-mode cache. Start and resume reapply that resolved
  preference along with the effective Android configuration. Effective night-mode changes reload resident
  sessions so existing documents observe the change reliably. System WebView replaces active
  renderer views against the new
  Android theme while carrying navigation and document state through an in-memory-only handoff,
  including for private tabs. Neither path replaces the browser controller or persists private
  state. An open Link Peek closes before its ephemeral preview is released.
- Night-change detection reads the effective Activity resources after AppCompat processes the
  configuration. AppCompat can dispatch nested callbacks when an app appearance override differs
  from the system; caching the incoming system value would hide a later return to System and leave
  Gecko's website media query on the previous light or dark mode.
- The main Activity reconciles appearance on start, resume and configuration callbacks. It reapplies
  the selected AppCompat night policy, distributes a fresh effective configuration to its view tree
  (including Compose), and forwards it to the browser runtime before reactivating sessions. This
  repairs views and Gecko's process-wide night-mode cache when background configuration delivery was
  missed, including while the Activity is visible but paused. Explicit light, dark and AMOLED choices
  still win over the device setting. Only an actual
  effective night-mode edge uses the existing page refresh path; an unchanged resume or stale-cache
  repair preserves the current document and tab. Controller initialization also refreshes the runtime
  configuration when a new Activity reuses an existing Gecko runtime.
- System WebView algorithmic darkening is off by default. The optional **Force dark mode on
  websites** setting allows System WebView to recolor sites without their own dark theme while the
  effective app appearance is dark. GeckoView has no equivalent API, so the control is disabled for
  that engine. Websites can still respond to `prefers-color-scheme`; forced darkening may cause
  display issues by altering author-defined colors and image assets.
- Website font size is global across regular and private browsing and changes only text rendered by
  websites, not Candy's own interface. Android applies the persisted 50–200% value through
  GeckoView's runtime-wide font-size factor. GeckoView requires a document reload, so Candy reloads
  only the selected existing tab after the slider interaction finishes; background tabs adopt the
  new factor on their next navigation or reload. New and restored sessions receive the factor before
  rendering. Manual sizing keeps GeckoView's automatic Android system-font adjustment disabled.
- Website canvas colors remain WebView-owned. Candy does not apply a separate light or dark
  background behind page content, so transparent documents keep their author-defined foreground
  and canvas contrast in light, dark and forced-dark configurations.
- Shape tokens affect browser chrome and controls; geometry owned by gesture or transition rules stays unchanged.
- Each top-level settings destination has a distinct leading icon on the settings home page.
- Developer options stay hidden until **About & legal** is long-pressed once. The global unlock
  persists across restarts. Its safe-area controls are bounded before persistence, apply live to
  Gecko and System WebView without reloading, and reset any pending fallback confirmation chain.
  The native-fallback override updates document policy before redispatching window insets so pages
  never retain both Candy's scrollable inset and a native top margin.
- Gecko edge-to-edge leaves negative CSS `top` values unchanged by default. The persisted
  **Add inset to negative top values** developer switch restores inset addition for fixed and
  sticky elements, applies live without a reload, and returns off when Gecko settings reset.
- HTTP password-manager selection lives only in Developer options. It stays Gecko-only, defaults
  off and requires an explicit insecure-HTTP warning confirmation each time it is enabled.
- Touch and input diagnostics are process-local and automatically return off after process restart.
  The copied diagnostic report contains build, engine and aggregate runtime state, but no page URL,
  tab identifier or profile name.
- The experimental-features section exposes only functional experiments; currently this is the
  global native safe-area fallback.
- Developer presentation actions show gesture onboarding or the bundled release notes again without
  resetting either completion store. Settings closes before the requested presentation opens.
- Full-screen platform overlays launched from Settings preserve the Settings destination below
  them. They own predictive-back handling, so a system edge gesture dismisses only the overlay and
  returns to Settings instead of reaching the underlying browser/settings back target.
- On Android, Candy Recall is an explicit opt-in under History & browser data. Its summary states that readable text
  from regular pages is stored locally for search and private tabs are never included. Turning it
  off clears stored Recall text; ordinary History remains governed by its own settings.
- Gestures owns the expanded address-bar action editor. The former standalone tab-button
  visibility switch is intentionally absent because **Tabs** is now an ordinary configurable action.
- Gestures owns the global long-press link action. Invalid stored values fall back to Link
  Peek. Regular and private tab targets distinguish foreground from background creation. Legacy
  stored choices retain regular-background and private-foreground behavior. Image-only long presses
  keep their content sheet, and private-open falls back to Link Peek when the active profile cannot
  create private tabs. Direct downloads reuse the source tab's engine-scoped context-download path;
  Gecko retains its referrer and private session context.
- Link Peek right-swipe action persists independently of the long-press link choice. Its selection-page link remains visible and disabled outside Link Peek;
  its localized search entry retains its existing Link Peek availability filter. Unknown stored action IDs
  fall back to background-tab open; left always closes the preview except during commit motion.
- Gestures also owns the Link Peek action editor. It reuses the address-action editor's
  breakaway, snap, settle, haptic and accessibility behavior. Its three configurable positions may
  hold unique actions or remain empty; dragging a toolbar action back to the palette clears that
  exact position. Only empty configurable positions advertise and accept drops; occupied actions
  and the fixed `+` at slot three show no drop marker. Duplicate, excess or unknown persisted actions
  normalize to positions without silently filling user-cleared slots.
- The Browser setting for the draggable page scroll bar is global and defaults off. When enabled,
  Gecko and System WebView expose native scroll metrics through the same engine port. A touch-sized
  overlay thumb appears during scrolling, maps direct dragging back to the owning engine, and fades
  after interaction. System WebView suppresses its built-in indicator while the overlay owns this
  affordance. Scrollbar position refreshes are capped at 60 Hz while unrelated browser-chrome scroll
  reactions use an optimized 60 Hz cap that can step down to 30 and 15 after sustained slow UI
  frames; developer options can select fixed 120, 60, 30 or 15 Hz instead. The overlay stays
  outside fullscreen and video-only presentation.
- Page translation provider is global and persists across regular and private browsing. Translation
  itself remains an explicit page action; no source URL or translated content is stored separately.
- **Prevent automatic video playback**, under Settings → Player, defaults on and is applied to every existing and newly
  created browser session. An explicitly stored user choice wins on both engines.
  Gecko's native content-permission delegate denies both audible and inaudible autoplay when the
  setting is enabled and explicitly allows both when disabled. Unrelated content permissions remain
  deferred to their dedicated handlers. Existing Gecko site permissions are synchronized to the
  global setting so an older site decision cannot override it. Because the current document also
  caches its autoplay decision, changing the setting reloads already navigated Gecko sessions only
  after both stored permission values confirm the new policy. GeckoView 157 does not expose a
  completion callback for permission writes, so Candy retries the read for at most two seconds. If
  confirmation times out, the current document stays unchanged instead of reloading under an
  unconfirmed policy. Permission reads use Gecko's reported URI, context ID and private-mode scope;
  private decisions therefore remain session-private. This path does not inject JavaScript into the
  page. Mozilla fixed the synchronous audible `play()` permission race in Gecko 154
  ([bug 2049064](https://bugzilla.mozilla.org/show_bug.cgi?id=2049064)). The named immediate-playback
  device regression is enabled and passes on GeckoView 157. Candy compiles the pinned GeckoView 157
  against Android SDK 37.1 with AGP 9.4.0; see the [upgrade audit](../audits/geckoview-157-upgrade.md).

## Address loading styles

| Style | Presentation | Progress contract |
| --- | --- | --- |
| Rainbow (default) | Animated rainbow halo and rounded progress border | Uses active engine percentage; moving colors do not change the measured segment length |
| Tonal | Theme primary/tertiary gradient, neutral outline track, breathing rounded stroke and soft halo | Same engine percentage and completion fade as Rainbow; theme colors stay in place |

The loading style is a global persisted appearance choice. Unknown stored style IDs fall back to
Rainbow. Selecting Tonal leaves the global animation setting unchanged. Disabling animations
continues to remove timed motion from both styles while retaining loading/progress semantics.

| Engine/UI state | Indicator |
| --- | --- |
| Loading with no positive progress yet (including address resolution) | Explicit indeterminate traveling segment |
| Loading with positive engine progress | Determinate segment, clamped to 0–100%, with matching accessibility progress |
| Completed active load | Short 280 ms completion fade, then hidden |
| Interrupted/failed load below completion | Hidden without completion claim |
| Idle/restored tab | Hidden |

`AddressLoadCapsuleFeedbackInstrumentedTest` covers both style semantics and
progress/completion/interruption transitions. Android forwards engine progress through the nullable `BrowserEngineEvent.progress` field. Gecko
includes progress-only state changes; System WebView publishes `WebChromeClient.onProgressChanged`.
`BrowserLoadingProgressRules` accepts progress/loading updates only for the active tab and current
navigation address, preserves missing values, and bounds reported percentages. Late callbacks cannot
restart a stopped load. Real slow-resource load, reload and stop coverage lives in
`BrowserLoadingProgressInstrumentedTest`; run each engine method in its own instrumentation process.

See [`../audits/issue-215-loading-progress.md`](../audits/issue-215-loading-progress.md) for the callback
path defect found while reviewing #204 and the measured verification scope.

## Settings search

| Contract | Behavior |
| --- | --- |
| Search presentation | Native Material 3 `SearchBar` and `SearchBarDefaults.InputField`, with a filled 56 dp pill, inline placeholder and theme colors. Results use native `ListItem` rows in one shared container below the bar, following the library-search pattern. |
| Empty query | Show the existing Settings Home categories below the search bar. |
| Matching | Match every word against localized title, description or page context, ignoring case and accents. Prefer exact titles, then title matches; preserve catalog order for ties. |
| Availability | Build entries from current browser/search engine, suggestion provider, flavor capabilities, download manager, appearance and developer unlock. Do not index unavailable conditional controls or user-owned data. |
| Navigation | Open the existing settings destination without changing a preference. Common choices, switches, links and sliders scroll the matched control into view and show a theme-colored outline. External Firefox extensions use the existing action. |
| Back and keyboard | Selecting a result hides the keyboard and clears focus. Returning to Home retains the query. Header/system Back on Home clears an active query before dismissing Settings. The clear button restores categories and lets the user keep typing. |
| Privacy | Query and target use `remember`, never saved state, preferences, history, logs or remote suggestions. Closing Settings discards them, including when opened from private browsing. |
| Verification | `SettingsSearchRulesTest`, `SettingsSearchCatalogTest`, `SettingsSearchInstrumentedTest`, `scripts/test_localization.py`. |
| Executed checks and screenshots | [`Settings search audit`](../audits/issue-253-settings-search.md). |

## Connected settings groups

Menu presentation and content layout form a connected group; all five lower-toolbar slots form a
second group. Bottom Sheet-specific choices stay visible and disabled under Floating, preserving
their values. Action-location choices retain their existing section order, platform availability,
allowed locations and change-only callbacks. Toolbar slots and action locations now open focused,
full-size selection dialogs with an approved Gestures header, existing icons and radio choices.
Selecting a value returns to Menu actions; header/device Back dismisses without writing. Empty slots,
entry filtering, normalization and disabled controls retain their prior behavior.
Shared Core browser, tabs/gestures, search and Topping
pages use the same groups. Unsupported shared-platform controls remain disabled; each Topping switch
is labeled by name, and its frame scope is disabled while the Topping is off without changing the
saved allowance. Edit and Delete remain independent management actions.

`BrowserMenuSettingsPageInstrumentedTest` covers five-slot visibility, retained values and large text;
`SharedCoreSettingsInstrumentedTest` exercises the public shared platform host for browser capability
boundaries, search validation, tab/menu navigation and Topping semantics.

`SettingsPreferenceGroup` supplies the theme’s extra-large exterior and keeps child control semantics
separate. Rows use the theme’s extra-small corners, preserving the existing Shape preference.
`SettingsPreferenceDivider` supplies the narrow page-colored gaps; shared controls inherit group
membership without requiring each caller to coordinate shapes. Settings pages use neutral
`surfaceContainerLow` backgrounds and opaque `surfaceContainerLowest` groups, independently of the
browser's frosted chrome preference. Lazy script/catalog rows use the same first/last corner rule.
Navigation rows consistently combine an icon, title, subtitle and trailing direction indicator.
Headers and preference labels wrap at larger font sizes.

Dependent choices remain visible and keep their stored values when inactive. This includes favorite
animation speed, Link Peek swipe action, frosted sliders, preview profile, download folder/1DM,
profile Guest Bubble, list origin, player seek and developer CSS controls. Device Search keeps selected
items visible but disabled while its master is off. Capability limitations still come from the existing
engine/platform state; this layout does not add engine support or change private persistence.

`SettingsGroupsInstrumentedTest` covers independent labeled switch semantics, retained disabled
checked values, rejected disabled writes and text wrapping at 200% font scale. Existing navigation,
search, capability and persistence tests continue to own their contracts. The source inventory is
captured before edits so reviewers can compare every prior option and dynamic enum owner.


### Explanatory selection pages

Colors and surfaces/shapes use inline radio groups with a Candy browser preview. Address-bar choices
use a generated classic/segmented motif with the shared artwork renderer's live theme coloring;
the illustration is decorative and displays both styles independently of the selection.
Tab-stack folders reuse the Hero/Grid/List preview. Long-press link and Link Peek swipe choices have
separate pages grouped by normal tabs, private tabs and other link actions. Foreground/background
choices keep their existing distinct labels. Candy Player has five explained modes, while autoplay
and the two seek values stay on its parent page. Closed-tab undo retains its functional illustration;
Device Search retains its controls and search/result illustration.

The bounded settings hierarchy now separates the following sections:

| Parent | Focused page | Retained controls |
| --- | --- | --- |
| Tabs | Tab lifecycle | Closed-tab undo link, resident limit and auto-close lifetime |
| Tabs | Profile options | Profiles master and dependent Guest Bubble |
| Gestures | Link interactions | Link long press, Link Peek swipe and Link Peek buttons |
| Gestures | Address bar buttons | Address long press, Address bar actions and Menu actions |
| Gestures | Gestures | Docking, viewport swipe, dismissal resistance and status-bar master/width |
| Browser → Web pages | Translations | All translation providers, explanations and capability guard |
| Browser → Web pages | Personal link rules | Existing rule list, editor, ordering and validation |
| Browser → Links & apps | External link preview | Master and dependent profile selection |

Each new section page begins with its approved artwork. Tab lifecycle and Personal link rules have
newly approved originals; the three gesture sections reuse the approved Gestures motif. External link
preview has exactly one v8 header: the older active/inactive graphic and the pill-shaped enclosing
master treatment are removed. The existing master, profile fallback and disabled-value behavior remain.

Search engines, suggestions/fallback, download managers, translation providers, auto-close lifetime
and preview profiles use inline radio choices. The four fixed Browser choices (engine, startup address
focus, favorite speed and external-app handling) also use radios. The download folder keeps its
existing edit dialog, reached through a right-chevron row. Menu slots/action locations use the focused
selection dialogs described above. Sync, security, developer, app-icon and language UI remain outside
this bounded change.

Selected radio rows share the neutral background of unselected rows; the primary-colored indicator
and selected semantics communicate state. Existing theme, disabled colors, values, callbacks and
storage remain authoritative. `SettingsRouter` retains saveable page state by destination so returning
to a parent restores its scroll position. Search targets move with their controls and retain all 146
existing static catalog IDs. The shared parent map drives header, system/predictive Back and reverse
transitions; opening a search result retains the existing return-to-search override. Search query
privacy is unchanged. The complete option and capability map is in
[settings-option-map.md](settings-option-map.md).

The preservation audit found all 117 keys in the complete `BrowserSessionStore` and its source
unchanged from the current base. This is a store-state inventory, not a count of visible settings.
The older 101-key count is historical and is not asserted as newly verified. The final affected
device suite passed 67 tests and skipped one permission-dependent case, with no failures.
Build, unit-test, lint and visual scope are recorded in the option map's
[current validation](settings-option-map.md#current-validation); older results apply only to
their explicitly identified revisions.

### Approved settings header artwork

The **Theme & interface**, **Tabs**, **Player**, **Search**, **Downloads**, **Gestures**,
**Profile options**, **Translations**, **Tab lifecycle**, **Personal link rules** and
**External link preview** pages show approved imagegen illustrations.
**Open links in apps** uses a matching generated browser/app-return motif; its source and prompt
are recorded in [`../design/external-app-links-artwork.md`](../design/external-app-links-artwork.md). Profiles and Translations
now begin their own focused pages with full headers; they are no longer compact inline section art.
Android and shared Core Search use the same approved Search illustration and built-in engine choices.
**Long-press link action** uses a matching generated hand/link/action-card motif instead of the
older geometric preview. Its original, exact prompt and rendering contract are recorded in
[`../design/link-long-press-artwork.md`](../design/link-long-press-artwork.md).
`SettingsHeaderIllustration` owns shared Compose rendering. The images are decorative, have no
accessibility announcement or interaction, and use a centered, aspect-preserving runtime window in a
200 dp header capped at 360 dp wide. Narrow layouts retain the complete shape. Artwork scrolls with
the page. The External link preview page uses only its approved v8 decorative header; its redundant
older graphic is removed without removing controls.

The **Gestures & controls** overview retains its swipe-arrow header. Its **Gestures** child uses a
hand dragging a floating address pill, while **Address-bar buttons** uses floating shortcut tiles
and placement slots. These three pages have independent artwork; the new originals and exact
prompts are recorded in [Gesture and address-bar artwork](../design/gesture-controls-artwork.md).

The eleven PNGs in `shared/src/commonMain/composeResources/drawable` are byte-identical original
downloads. No pixels were cropped, recolored, redrawn or regenerated in the source files. The
Appearance and Tabs windows reference the first sheet; the rejected rounded Player on that sheet
is outside both windows. Player references only the separately approved waveform image.

| Resource | Original pixels | Source window `(left, top, right, bottom)` | SHA-256 |
| --- | --- | --- | --- |
| `settings_approved_appearance_tabs.png` | 1024 × 1536 | Appearance `(160, 80, 860, 610)`; Tabs `(160, 615, 860, 1060)` | `f550651600031493d981511ea9ef0b0b7e38a1c608ec911597d12f824dd3eafc` |
| `settings_approved_player_waveform.png` | 1536 × 1024 | Player `(160, 245, 1376, 780)` | `852cdeed7a16d1fc54c75e5dda13138bcbf83fd8f96acfbb5321e0c760b88a02` |
| `settings_approved_search_engines.png` | 1536 × 1024 | Search `(160, 250, 1376, 750)` | `65fd3d6b9e012ea768f71046b52a17bca96a606c75097c778e1589b743644d25` |
| `settings_approved_translations.png` | 1536 × 1024 | Translations `(220, 250, 1320, 770)` | `b1e0da1d08e3f5054b1d252404d3b7fd549a402be6d5e01f9f7dd879740fd438` |
| `settings_approved_downloads_v2.png` | 1536 × 1024 | Downloads `(260, 140, 1300, 880)` | `866d7e36ecc1c21ba6602572748af2cf80551050633c10620d9384ebef3c925f` |
| `settings_approved_profiles_v2.png` | 1536 × 1024 | Profiles `(50, 270, 1486, 700)` | `8cd254c78c0663487bcef495b50f6456ac7e8a2bd8097c27669184e48365f828` |
| `settings_approved_gestures.png` | 1536 × 1024 | Gestures `(160, 245, 1376, 780)` | `3f20d792611da34ee2aecc42cee5e499cea90382c8149474770815a0cdf6e696` |
| `settings_approved_link_preview_final_v8.png` | 1536 × 1024 | Link Preview `(0, 0, 1536, 1024)` | `96d31d9d40b7e48b5cedde34da2ce48e3b8f2169663bdb3f2324111743c5648a` |
| `settings_approved_tab_lifecycle.png` | 1536 × 1024 | Tab lifecycle `(340, 115, 1210, 910)` | `0178005781cbd91cec822e02480b276de186d1dc172d2e1d39bff7f57d49ebf5` |
| `settings_approved_personal_link_rules.png` | 1536 × 1024 | Personal link rules `(140, 225, 1400, 795)` | `63e9bd5762063cce660ad691b6afdb153f5b3e9378e5601ceb71792e0b08fad0` |
| `settings_external_app_links.png` | 1536 × 1024 | Open links in apps `(160, 205, 1385, 815)` | `288d106373b03999124b7aaeaa0342197d108aea3b8b4aace783c660e8fba6fe` |

The original Library IDs are `libfile_46c900dcd0448191a03ae81118385873` and
`libfile_48290d7ff1088191aacd6d776d5db0db`, respectively. These are user-approved imagegen
outputs, not third-party illustration downloads.

The six additional originals use Library IDs `libfile_ab3362e52d6081919f27fac1fa7be5b8`
(Search), `libfile_60e06317312c81919708dcab3dd91cd6` (Translations),
`libfile_d1903c491e74819194cf6df706277922` (Downloads v2),
`libfile_3923a9b6d6608191ab14636ac216ac87` (Profiles v2),
`libfile_444e2ee636808191b0688c9dafbf2fbc` (Gestures), and
`libfile_d3e00e4f6340819189a159113a486d6e` (Link Preview final v8).
The two later approved originals use `libfile_f834b2fd22248191a2c216913635f9f0` (Tab lifecycle)
and `libfile_1c6153dd3f7c8191966fcc18569aaf93` (Personal link rules). Their original and resource bytes
were verified identical; the runtime windows retain their complete artwork with paper around the
edges (minimum sampled border luminance 252 and 253 respectively). No source pixel edit was made.

The Downloads window includes the cloud, complete dotted trail and folder. Link Preview deliberately
uses the full original so the hand and wrist near its bottom edge are never clipped.

The nearly monochrome originals contain opaque offwhite paper with slight RGB variation. At draw
time, a luminance matrix maps the paper's white point of 244/255 to white, retaining lower tonal
detail. A separate layer maps black to the current Material `primary` and white to the actual
`settingsPageColor()` (`surfaceContainerLow`), including the now-separate Translations page.
The intermediate clamp is necessary: combining
these matrices would extrapolate the offwhite background beyond the page color in dark palettes.
Both passes keep the alpha row unchanged, and color-matrix offsets use the API's 0–255 units.
The matrices update with the live Material palette, including Dynamic, Candy, Neutral, dark and
AMOLED. This is a tonal filter, rather than a flat silhouette tint.

`SettingsHeaderArtworkRulesTest` checks source boundaries, aspect-preserving fit, paper normalization,
tonal and alpha preservation, and palette changes. `SettingsHeaderArtworkInstrumentedTest` captures
the actual page composables for all eleven illustrations in all nine palette/mode combinations,
checks the rendered border pixels, live theme colors and tonal detail, and exercises scrolling and
settings changes at 320 dp / 200% font scale. The Link Preview original has softer dark grays, and its
contrast assertion reflects those approved source tones. `SharedCoreSettingsInstrumentedTest` also
captures the shared Search header and exercises its existing provider/instance validation flow.
The artwork suite's unscaled screenshots and capture inventory are saved in the test app's
external-files `settings-header-artwork` directory; the shared-host capture is in
`settings-combined-evidence`. The artwork suite uses Compose page captures, not browser-navigation tests.
The suite retains live theme changes in the same composition and waits for a rendered frame after
header pixel checks before saving the full-page capture. The final eleven-motif device run passed
all 99 theme combinations and all 11 narrow/large-text cases. Prior nine-motif screenshots remain
evidence only for their earlier layout. Real-app navigation, Back behavior and persistence passed
separately in `PlayerSettingsNavigationInstrumentedTest` and the affected settings/search suites;
they are not established by artwork captures alone. One private-search recreation case skipped
because notification permission was not already granted.
The existing Colors and Surfaces/shapes previews continue to use their functional illustrations;
Gestures does not receive the Tabs header. Address bar uses the generated motif documented in
[Address-bar artwork](../design/address-bar-artwork.md). Candy Player mode
uses five generated flat Material Expressive images, one per selection, in the former 400 dp ×
240 dp preview slot. They share the approved headers' live theme coloring and retain the complete
original images. Sources, prompts and checks are in [Player mode artwork](../design/player-mode-artwork.md).

Tab overview selection uses three matching flat Material Expressive images for Coverflow, Grid
and List, retaining the 400 dp × 260 dp preview slot. The same shared renderer applies live theme
coloring and fits each original in full. Sources, prompts and checks are in
[Tab overview artwork](../design/tab-overview-artwork.md).
