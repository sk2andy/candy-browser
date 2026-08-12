# Testing rules

## Layer lookup

| Behavior | Location | Convention |
| --- | --- | --- |
| Pure rules, parsers, reducers, models | `app/src/test/...` | JUnit 4; no Android dependency |
| Android store, intent, permission, WebView | `app/src/androidTest/...` | Name `*InstrumentedTest` |
| Compose behavior/semantics | `app/src/androidTest/.../ui` | Exercise visible state and user action |
| Generator/compiler | `scripts/test_*.py` or `scripts/*.test.mjs` | Run as deterministic CLI test |

## Test shape

- Mirror production package and primary type name.
- Name JVM tests with backticked behavioral sentences: ``fun `same origin rejects scheme changes`()``. Keep instrumented tests camelCase to match the existing Android suites.
- Assert observable outcomes with independent literals or domain values; avoid reimplementing production logic in expected values.
- Keep setup inline when short. Extract private fixtures/helpers only when they remove noise.
- Cover valid path, boundary, malformed/stale input and private-mode behavior when relevant.
- Add a regression test at the lowest seam that reproduces the real bug pattern.
- Prefer pure JVM coverage for policy. Add instrumentation only when Android, WebView, lifecycle, storage or Compose semantics are essential.
- Keep async tests deterministic: explicit idle/wait hooks, bounded timeouts and stable identities.
- Test shared pure invariants once, then retain per-store coverage for round trips, corrupt data, pruning, interrupted writes and caller-specific lifecycle behavior.
- When consolidating duplicate helper tests, verify no observable boundary scenario disappears.

## Handoff lookup

| Change | Evidence |
| --- | --- |
| Pure Kotlin only | Relevant focused test plus `testDebugUnitTest` |
| Android/Compose/WebView | Focused instrumented result and applicable JVM suite |
| Generated asset | Compiler/generator tests and clean second-run diff |
| Unrun device test | Exact command/test and missing device/environment blocker |
