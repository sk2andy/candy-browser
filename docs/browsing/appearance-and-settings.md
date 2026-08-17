# Appearance and settings

## Ownership

| Layer | Responsibility | Main code |
| --- | --- | --- |
| Model | Stable, persisted appearance choices and safe fallback values | `data/AppearanceSettings.kt` |
| Persistence | Global appearance preference round trips | `data/BrowserSessionStore.kt` |
| State | Observable selection and update wiring | `browser/BrowserController.kt` |
| Theme | Color schemes, surface treatment, shape tokens and AMOLED surfaces | `ui/theme/MaterialBrowserTheme.kt` |
| UI | Appearance destination and live selection controls | `ui/SettingsScreen.kt` |
| System bars | Status/navigation icon contrast for forced light and dark modes | `AppearanceSystemBars.kt` |
| Toppings | Local editor/import plus explicit GitHub catalog discovery; browser runtime and remote state stay controller-owned | `ui/UserscriptManagementScreen.kt`, `ui/ToppingCatalogScreen.kt` |

## Choices

| Setting | Values | Default |
| --- | --- | --- |
| Appearance | System, light, dark, AMOLED | System |
| Color palette | Material You, Candy, neutral | Material You |
| Surfaces | Clear, frosted | Clear |
| Shape | Angular, rounded, extra rounded | Rounded |

### Surface semantics

| Surface | Browser chrome treatment |
| --- | --- |
| Clear | Opaque neutral containers with standard elevation |
| Frosted | Light translucent chrome with a live blur of WebView content behind it |

Frosted exposes three persisted controls while selected:

| Control | Range | Default |
| --- | --- | --- |
| Transparency | 0–80% | 40% |
| Address-bar transparency | 0–80% | 40% |
| Blur strength | 0–100% | 60% |

## Invariants

- Appearance settings are global and persist across normal and private browsing.
- Unknown stored values fall back per field; one corrupt value does not discard valid choices.
- AMOLED keeps root surfaces black. Frosted transparency does not override AMOLED black chrome.
- Frosted changes only Candy browser chrome. It does not inject styles into websites or claim backdrop refraction.
- Frosted falls back to a tinted translucent surface when no WebView blur source is visible, such as the new-tab page or tab overview.
- General transparency controls menus and other browser chrome; address-bar transparency independently controls the browsing and tab-overview address bars.
- Tab options use that translucent Frosted fallback over tab-overview cards instead of an opaque menu surface.
- The main `…` menu shares the visible WebView blur source; its rows remain translucent so the effect stays visible.
- Bottom sheets use the general Frosted transparency setting; Clear and AMOLED sheets remain opaque.
- Forced light, dark and AMOLED modes update system-bar icon contrast independently from system night mode.
- Shape tokens affect browser chrome and controls; geometry owned by gesture or transition rules stays unchanged.
- Each top-level settings destination has a distinct leading icon on the settings home page.
