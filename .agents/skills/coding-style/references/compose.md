# Compose rules

## Component lookup

| Concern | Convention |
| --- | --- |
| Screen entry | Use a domain-named `@Composable` and explicit callbacks/state when practical |
| State owner | Keep browser/platform state in controller or focused state holder; use `remember` for UI-local ephemeral state |
| Restoration | Use `rememberSaveable` only for saveable UI state that should survive recreation |
| Effects | Key `LaunchedEffect`/`DisposableEffect` to the identity that owns the work; cancel/close resources on exit |
| Text/content | Use `stringResource` and existing localized resources for user-facing strings |
| Theme | Use `MaterialTheme`/Candy palette and existing shapes/motion before hard-coded styling |
| Accessibility | Provide semantics/content descriptions for icon-only actions and keep controls test-addressable |

## Interaction and motion

- Put thresholds, progress, layout, physics and reducer math in pure `*Rules`/motion types.
- Keep pointer input and animation execution in Compose; emit one semantic action after rule resolution.
- Clamp progress, sizes, scale and pan inputs; define deterministic behavior for zero/invalid dimensions.
- Hoist state when parent/controller coordinates multiple surfaces. Keep component-only animation state local.
- Preserve edge-to-edge/window-inset behavior when adding overlays, sheets or browser chrome.
- Prefer focused components over adding unrelated regions to `BrowserScreen.kt`.

## Verification lookup

| Behavior | Test layer |
| --- | --- |
| Layout/gesture/motion math | JVM test against rule object |
| Semantics, visibility, clicks, state restoration | Compose instrumented test |
| WebView under Compose overlay | Focused instrumented integration test |

