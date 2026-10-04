# Appearance and settings

## Ownership

| Layer | Responsibility | Main code |
| --- | --- | --- |
| Model | Stable, persisted appearance choices and safe fallback values | `shared/src/commonMain/.../data/AppearanceSettings.kt` |
| Persistence | Global appearance preference round trips | `data/BrowserSessionStore.kt` |
| State | Observable selection and update wiring | `browser/BrowserController.kt` |
| Theme | Platform design language, color schemes, motion, Android night resources, root/system-bar wiring, website color-scheme and font-size preferences, surface treatment, shape tokens and AMOLED surfaces | `MainActivity.kt`, `AppearanceNightMode.kt`, `browser/BrowserController.kt`, `browser/gecko/GeckoRuntimeOwner.kt`, `ui/theme/CandyDesignSystem.kt`, `ui/theme/MaterialBrowserTheme.kt` |
| Settings routing | Shared destination model, transition, home, controls and core pages; platform resources, icons, effects and persisted state stay adapters | `shared/src/commonMain/.../SettingsDestination.kt`, `shared/src/commonMain/.../ui/settings`, Android `ui/SettingsScreen.kt` adapters |
| Android settings search | Memory-only search on Settings Home matches localized option titles, descriptions and destination context. Capability-filtered results open existing routes and highlight/scroll common controls into view. | `ui/SettingsSearchRules.kt`, `ui/SettingsSearchCatalog.kt`, `ui/SettingsSearchField.kt`, shared `SettingsSearchTarget.kt` |
| Android app language | Settings → Browser → App language uses native language names and a Follow device language option. Android's `LocaleManager` owns persistence and synchronization with system app-language settings. The supported list comes from `res/xml/locales_config.xml`; all supported languages and localization checks are listed in [`app-languages.md`](app-languages.md). | `data/AppLanguagePreferences.kt`, `ui/AppLanguageSettings.kt`, `ui/BrowserSettingsOverlay.kt`, `res/values-*/strings.xml` |
| Language changes | MainActivity handles locale and layout-direction configuration updates without recreation, retaining open regular/private tabs and the current settings page. Localized Android resources refresh through the existing configuration dispatch; device-language changes apply when no app override is selected. | `MainActivity.kt`, `AndroidManifest.xml`, `AppLanguageSettingsInstrumentedTest`, `AppLanguagePreferencesInstrumentedTest` |
| Player settings | Direct Settings → Player page for video autoplay, Candy Player mode and independent seek sliders; header and Android Back return to Settings Home. Browser no longer duplicates these controls; shared/iOS keeps the unavailable route disabled. | `ui/PlayerSettingsPage.kt`, `ui/InlineMediaPlayerSeekSlider.kt`, `SettingsDestination.Player`, `PlayerSettingsPageInstrumentedTest`, `PlayerSettingsNavigationInstrumentedTest` |
| Appearance UI | Shared production destination and controls; Android supplies live persisted state, iOS shows them disabled until it owns equivalent state | `shared/src/commonMain/.../ui/settings/AppearanceSettingsPage.kt`, Android `ui/AppearanceSettingsPage.kt` adapter |
| Address-bar actions | Persisted ordered action layout plus drag-editor navigation under Tabs & gestures | `data/AddressBarActionLayout.kt`, `ui/AddressBarActionEditor.kt`, `BrowserSessionStore` |
| Page scroll bar | Persisted opt-in, engine-neutral scroll metrics and draggable auto-hide overlay | `BrowserSessionStore`, browser-engine session ports, `ui/BrowserScrollBar` |
| Developer options | Persisted hidden unlock, bounded safe-area fallback tuning, insecure-HTTP autofill override, process-local input diagnostics, privacy-safe runtime report, [opt-in app logging and export](app-logging.md), and presentation replay actions | `DeveloperSettings`, `BrowserSessionStore`, `AppLogging`, `BrowserInputDiagnostics`, `DeveloperDiagnosticsReport`, `BrowserController`, `MainActivity`, `ui/DeveloperOptionsSettingsPage` |
| System bars | Status/navigation icon contrast for forced light and dark modes | `AppearanceSystemBars.kt` |
| Toppings | Local editor/import plus explicit GitHub catalog discovery; browser runtime and remote state stay controller-owned | `ui/UserscriptManagementScreen.kt`, `ui/ToppingCatalogScreen.kt` |
| App data archive | SAF launch and confirmation stay in the activity and Protection page; bounded ZIP policy and cold-process restore stay in focused data/transfer owners | `MainActivity.kt`, `ui/ProtectionSettingsPage.kt`, `data/AppDataArchive*`, `AppDataTransferActivity.kt` |
| Android backup | Encrypted cloud and device-transfer inclusion policy | `res/xml/data_extraction_rules.xml`, [`app-data-archive.md`](app-data-archive.md#android-auto-backup) |
| Candy Recall | Disabled-by-default local readable-page indexing and clear-on-disable behavior | `BrowserSessionStore`, `RecallRepository`, [`recall.md`](recall.md) |

## Choices

| Setting | Values | Default |
| --- | --- | --- |
| Appearance | System, light, dark, AMOLED | System |
| Animations | Off, on | On |
| Force dark mode on websites | Off, on | Off |
| Website font size | 50–200% in 5% steps | 100% |
| Color palette | Material You, Candy, neutral | Material You |
| Address-bar color | Theme, dimmed, graphite, black, custom RGB hex | Theme |
| Surfaces | Clear, frosted | Clear |
| Shape | Angular, rounded, extra rounded | Rounded |
| Address bar style | Classic, segmented | Classic |
| Address-bar loading indicator | Rainbow, tonal | Rainbow |
| Startup animation | Off, on | On |
| Address focus on launch | When startup animation is off, every launch, never | When startup animation is off |
| Open home page on startup | Off, on | Off |
| Long-press link action | Link Peek, copy, share, download, regular foreground/background tab, private foreground/background tab | Link Peek |
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

Android and iOS compile the same settings destination model, transition, page shell, controls, home
ordering and core page renderers from `shared/src/commonMain`. The shared settings home groups
destinations by task: Browsing, Personalization, Privacy & data, and More information. Android's
Browser page labels Browser setup, Startup, Favorites, Web pages, and Links & apps; Protection & data labels its
privacy tools, protection controls, history, and app-data actions. These are navigation labels only;
setting ownership and persistence do not change. Android resolves existing localized
resources, drawable icons, frosted container color and persisted state through thin adapters. Appearance
is fully shared; Tabs & Gestures shares overview-mode and dismiss-resistance controls; Browser shares the
translation-provider control. iOS routes to those shared pages without SwiftUI replacements. Its existing
tab-overview state and translation-provider choice are live and persisted; settings without equivalent
iOS backend state stay visibly disabled. Other
destination bodies remain pending.

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

Frosted exposes three persisted controls while selected:

| Control | Range | Default |
| --- | --- | --- |
| Transparency | 0–80% | 40% |
| Address-bar transparency | 0–80% | 40% |
| Blur strength | 0–100% | 60% |

## Invariants

- Appearance settings are global and persist across normal and private browsing.
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
- Candy Recall is an explicit opt-in under Protection & data. Its summary states that readable text
  from regular pages is stored locally for search and private tabs are never included. Turning it
  off clears stored Recall text; ordinary History remains governed by its own settings.
- Tabs & gestures owns the expanded address-bar action editor. The former standalone tab-button
  visibility switch is intentionally absent because **Tabs** is now an ordinary configurable action.
- Tabs & gestures owns the global long-press link action. Invalid stored values fall back to Link
  Peek. Regular and private tab targets distinguish foreground from background creation. Legacy
  stored choices retain regular-background and private-foreground behavior. Image-only long presses
  keep their content sheet, and private-open falls back to Link Peek when the active profile cannot
  create private tabs. Direct downloads reuse the source tab's engine-scoped context-download path;
  Gecko retains its referrer and private session context.
- Tabs & gestures also owns the Link Peek action editor. It reuses the address-action editor's
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
| Empty query | Show the existing Settings Home categories below the Material search field. |
| Matching | Match every word against localized title, description or page context, ignoring case and accents. Prefer exact titles, then title matches; preserve catalog order for ties. |
| Availability | Build entries from current browser/search engine, suggestion provider, flavor capabilities, download manager, appearance and developer unlock. Do not index unavailable conditional controls or user-owned data. |
| Navigation | Open the existing settings destination without changing a preference. Common choices, switches, links and sliders scroll the matched control into view and show a theme-colored outline. External Firefox extensions use the existing action. |
| Back and keyboard | Selecting a result hides the keyboard and clears focus. Returning to Home retains the query. Header/system Back on Home clears an active query before dismissing Settings. The clear button restores categories and lets the user keep typing. |
| Privacy | Query and target use `remember`, never saved state, preferences, history, logs or remote suggestions. Closing Settings discards them, including when opened from private browsing. |
| Verification | `SettingsSearchRulesTest`, `SettingsSearchCatalogTest`, `SettingsSearchInstrumentedTest`, `scripts/test_localization.py`. |
| Executed checks and screenshots | [`Settings search audit`](../audits/issue-253-settings-search.md). |
