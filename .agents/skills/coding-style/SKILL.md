---
name: coding-style
description: Apply Candy Browser's project-specific Kotlin, Jetpack Compose, Android/WebView, testing, and generator conventions. Use for any production-code, test, build-script, filter-asset, refactor, review, or bug-fix change in this repository.
---

# Candy Browser coding style

## Reference lookup

| Change | Read | Apply |
| --- | --- | --- |
| Ownership, package boundaries, state or side effects | [`references/architecture.md`](references/architecture.md) | Put policy, orchestration, UI and storage in their existing seams |
| Kotlin model, rule, controller, store or repository | [`references/kotlin.md`](references/kotlin.md) | Follow official Kotlin style and local type/function patterns |
| Compose screen, component, theme, motion or gesture | [`references/compose.md`](references/compose.md) | Hoist behavior, isolate motion math and use Material/theme semantics |
| JVM or Android instrumented test | [`references/testing.md`](references/testing.md) | Choose the lowest valid test layer and mirror package/name |
| Shell, Python, Node, generated asset or audit | [`references/scripts-and-assets.md`](references/scripts-and-assets.md) | Keep generation deterministic, pinned, licensed and testable |
| Cross-cutting change | All matching references above | Apply every affected rule set |

## Workflow lookup

| Order | Action | Completion criterion |
| ---: | --- | --- |
| 1 | Inspect neighboring production code and mirrored tests | Existing ownership and naming pattern identified |
| 2 | Read only matching references above | Every changed surface has a loaded rule source |
| 3 | Implement smallest coherent change | Deterministic policy remains outside Android/Compose wiring where possible |
| 4 | Run checks routed by [`AGENTS.md`](../../../AGENTS.md#verification) | Relevant checks pass or exact blocker is reported |
| 5 | Update matching feature docs | Changed behavior, invariant, storage, or ownership is discoverable |

