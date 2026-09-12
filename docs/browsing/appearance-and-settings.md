# Appearance and settings

## Ownership

| Layer | Responsibility | Main code |
| --- | --- | --- |
| Model | Stable, persisted appearance choices and safe fallback values | `shared/src/commonMain/.../data/AppearanceSettings.kt` |
| Persistence | Global appearance preference round trips | `data/BrowserSessionStore.kt` |
| State | Observable selection and update wiring | `browser/BrowserController.kt` |
| Theme | Platform design language, color schemes, motion, Android night resources, root/system-bar wiring, website color-scheme and font-size preferences, surface treatment, shape tokens and AMOLED surfaces | `MainActivity.kt`, `AppearanceNightMode.kt`, `browser/BrowserController.kt`, `browser/gecko/GeckoRuntimeOwner.kt`, `ui/theme/CandyDesignSystem.kt`, `ui/theme/MaterialBrowserTheme.kt` |
| Settings routing | Shared destination model, transition, home, controls and core pages; platform resources, icons, effects and persisted state stay adapters | `shared/src/commonMain/.../SettingsDestination.kt`, `shared/src/commonMain/.../ui/settings`, Android `ui/SettingsScreen.kt` adapters |
| Appearance UI | Shared production destination and controls; Android supplies live persisted state, iOS shows them disabled until it owns equivalent state | `shared/src/commonMain/.../ui/settings/AppearanceSettingsPage.kt`, Android `ui/AppearanceSettingsPage.kt` adapter |
| Address-bar actions | Persisted ordered action layout plus drag-editor navigation under Tabs & gestures | `data/AddressBarActionLayout.kt`, `ui/AddressBarActionEditor.kt`, `BrowserSessionStore` |
| Page scroll bar | Persisted opt-in, engine-neutral scroll metrics and draggable auto-hide overlay | `BrowserSessionStore`, browser-engine session ports, `ui/BrowserScrollBar` |
| Developer options | Persisted hidden unlock, bounded safe-area fallback tuning, insecure-HTTP autofill override, process-local input diagnostics, privacy-safe runtime report and presentation replay actions | `DeveloperSettings`, `BrowserSessionStore`, `BrowserInputDiagnostics`, `DeveloperDiagnosticsReport`, `BrowserController`, `MainActivity`, `ui/DeveloperOptionsSettingsPage` |
| System bars | Status/navigation icon contrast for forced light and dark modes | `AppearanceSystemBars.kt` |
| Toppings | Local editor/import plus explicit GitHub catalog discovery; browser runtime and remote state stay controller-owned | `ui/UserscriptManagementScreen.kt`, `ui/ToppingCatalogScreen.kt` |
| App data archive | SAF launch and confirmation stay in the activity and Protection page; bounded ZIP policy and cold-process restore stay in focused data/transfer owners | `MainActivity.kt`, `ui/ProtectionSettingsPage.kt`, `data/AppDataArchive*`, `AppDataTransferActivity.kt` |
| Android backup | Encrypted cloud and device-transfer inclusion policy | `res/xml/data_extraction_rules.xml`, [`app-data-archive.md`](app-data-archive.md#android-auto-backup) |
| Candy Recall | Disabled-by-default local readable-page indexing and clear-on-disable behavior | `BrowserSessionStore`, `RecallRepository`, [`recall.md`](recall.md) |

## Choices

| Setting | Values | Default |
| --- | --- | --- |
| Appearance | System, light, dark, AMOLED | System |
| Force dark mode on websites | Off, on | Off |
| Website font size | 50–200% in 5% steps | 100% |
| Color palette | Material You, Candy, neutral | Material You |
| Surfaces | Clear, frosted | Clear |
| Shape | Angular, rounded, extra rounded | Rounded |
| Startup animation | Off, on | On |
| Open home page on startup | Off, on | Off |
| Long-press link action | Link Peek, copy, share, download, regular foreground/background tab, private foreground/background tab | Link Peek |
| Candy Recall | Off, on | Off |
| Page translation provider | Google Translate, Yandex Translate, Kagi Translate | Yandex Translate on Android; Google Translate on iOS |
| Prevent automatic video playback | Off, on | Off |
| Developer safe-area layout quiet | 100–800 ms in 50-ms steps | 400 ms |
| Developer safe-area failed checks | 2–5 | 3 |
| Force native safe-area fallback | Off, on | Off |
| Touch and input diagnostics | Off, on for current process | Off |

## Cross-platform settings migration

Android and iOS compile the same settings destination model, transition, page shell, controls, home
ordering and core page renderers from `shared/src/commonMain`. Android resolves existing localized
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
- Open home page on startup is global and disabled by default. When enabled, normal cold and warm
  launcher opens select a fresh blank tab while keeping restored tabs. An existing fresh regular
  blank tab in the active profile is reused. External links, launcher shortcuts, Site Capsules, and
  activity recreation keep their own destinations.
- Unknown stored values fall back per field; one corrupt value does not discard valid choices.
- AMOLED keeps root surfaces black. Frosted transparency does not override AMOLED black chrome.
- Frosted changes only Candy browser chrome. It does not inject styles into websites or claim backdrop refraction.
- Frosted uses WebView blur sources while browsing and Compose-backed blur sources on the new-tab page and tab overview.
- The status-bar protection is a static surface-tint fade drawn above page content. It never samples
  or continuously invalidates the browser engine; the optional Frosted address chrome keeps its
  separate live blur source.
- General transparency controls menus and other browser chrome; address-bar transparency independently controls the browsing and tab-overview address bars.
- Blur strength is global across frosted address chrome, menus, search suggestions and supported sheets.
- Tab options blur the visible tab-overview cards behind the menu instead of falling back to a sharp translucent surface.
- The main `…` menu shares the active browser-content blur source, including the new-tab page; its rows remain translucent and its individual quick-action tiles use the configured blur strength. It opens from the address-bar action with a spring scale-and-rise transition and leaves with a short fade-and-shrink transition.
- Bottom sheets use the general Frosted transparency setting; Privacy X-Ray also blurs the active browser content. Clear and AMOLED sheets remain opaque.
- Forced light, dark and AMOLED modes update system-bar icon contrast independently from system night mode.
- Appearance mode also selects Android's activity night resources. Both browser engines therefore
  expose the same effective light or dark mode to websites through `prefers-color-scheme`; AMOLED is
  dark, while System follows the device setting. GeckoView receives the runtime color-scheme
  preference plus Android configuration changes while System is active, then reloads resident
  sessions so existing documents observe the change reliably. System WebView replaces active
  renderer views against the new
  Android theme while carrying navigation and document state through an in-memory-only handoff,
  including for private tabs. Neither path replaces the browser controller or persists private
  state. An open Link Peek closes before its ephemeral preview is released.
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
- **Prevent automatic video playback** defaults on and is applied to every existing and newly
  created browser session. An explicitly stored user choice wins on both engines.
  Gecko's native content-permission delegate denies both audible and inaudible autoplay when the
  setting is enabled and explicitly allows both when disabled. Unrelated content permissions remain
  deferred to their dedicated handlers. Existing Gecko site permissions are synchronized to the
  global setting so an older site decision cannot override it. Because the current document also
  caches its autoplay decision, changing the setting reloads already navigated Gecko sessions only
  after both stored permission values confirm the new policy. GeckoView 155 does not expose a
  completion callback for permission writes, so Candy retries the read for at most two seconds. If
  confirmation times out, the current document stays unchanged instead of reloading under an
  unconfirmed policy. Permission reads use Gecko's reported URI, context ID and private-mode scope;
  private decisions therefore remain session-private. This path does not inject JavaScript into the
  page. GeckoView 155 can still reject a synchronous audible `play()` before its asynchronous
  embedder permission result arrives (Mozilla bug 2049064). The exact case remains a named skipped
  device regression until Mozilla ships the platform fix. Candy now compiles GeckoView 155 against
  Android SDK 37.1 with AGP 9.4.0.
