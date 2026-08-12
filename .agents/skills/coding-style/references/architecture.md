# Architecture rules

## Ownership lookup

| Concern | Owner | Rule |
| --- | --- | --- |
| Android lifecycle, intents, activity-result contracts | `MainActivity` / dedicated activity | Keep browser policy out of activity code |
| Observable browser state, WebViews, platform orchestration | `BrowserController` | Add wiring here; extract new deterministic decisions |
| Compose rendering and interaction | `ui/` | Read state and emit actions; keep storage/platform work outside composables |
| Browser models and policy | `browser/` and focused subpackages | Prefer immutable models, sealed outcomes and pure `*Rules` objects |
| Persistence | `data/` store/repository | Validate at the store boundary; serialize off-main I/O |
| Filtering/privacy | `blocking/`, `browser/permissions/` | Reuse central validators and policy boundaries |
| Feature-specific model | `capsule/`, `reader/`, owning package | Keep feature vocabulary and invariants together |

## Change rules

- Put deterministic branching, normalization, retention, layout and reducer logic in focused `object …Rules` types.
- Keep `MainActivity`, `BrowserController`, and `BrowserScreen` changes to necessary wiring when a focused seam exists.
- Pass dependencies into focused classes when behavior varies or platform access needs isolation.
- Use immutable snapshots across async boundaries. Reject callbacks whose tab, request, profile, or navigation identity is stale.
- Use `applicationContext` in long-lived stores/repositories. Serialize writes per resource; return UI callbacks on main where required.
- Normalize and bound untrusted/persisted input before it enters runtime state.
- Preserve memory-only private state at every storage and network boundary.
- Reuse existing policy such as `BrowserUriPolicy`, `PermissionOrigin`, `CandyRuleValidator`, and feature `sanitize` functions.
- Share mechanics only after identifying a stable invariant across callers. Keep differences such as retention, cleanup and error reporting explicit at each owner.
- Treat best-effort cleanup as an API contract, not an accidental ignored result. Surface deletion failure when completeness matters.

## File-growth rule

| Existing large file | Accept | Extract |
| --- | --- | --- |
| `BrowserController.kt` | State connection, WebView callback wiring, orchestration call | New pure policy, parser, store, adapter, reducer |
| `BrowserScreen.kt` | Root composition and feature-state wiring | Focused screen/component, layout rule, gesture/motion math |
| `BrowserSessionStore.kt` | Small existing-preference addition | Independent file-backed feature store or complex mutation policy |
