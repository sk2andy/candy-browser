# Kotlin rules

## Baseline

| Area | Convention |
| --- | --- |
| Style | Follow `kotlin.code.style=official`; use 4-space indentation and trailing commas in multiline declarations/calls |
| Imports | Use explicit imports; keep package-first file layout; remove unused imports |
| Visibility | Use the narrowest useful visibility; prefer `internal` for package/module helpers and `private` for implementation detail |
| State | Prefer `val`, immutable `data class` snapshots and `copy`; keep mutation owned by controller/repository/state holder |
| Naming | Use domain names; suffix pure policy with `Rules`, persistence with `Store`/`Repository`, Android launch edges with role names |
| Constants | Put stable bounds/wire values in the owning type/companion; use uppercase names |

## Function shape

- Use expression bodies for short projections and decisions.
- Use early returns for invalid/stale input; keep the main path shallow.
- Use named arguments when multiple same-shaped values or booleans would hide meaning.
- Return typed nullable/sealed outcomes for expected rejection. Use `require`/`check` for programmer or invariant failures.
- Bound collections, strings, bytes and timestamps at input/persistence boundaries.
- Keep normalization idempotent and deterministic; give tie-breakers to sorted output.
- Use `runCatching` around parsing/platform/file failure boundaries, then map failure to a safe explicit result.
- Avoid speculative interfaces: add a seam when callers/adapters actually vary.

## State and concurrency

| Case | Pattern |
| --- | --- |
| Compose-visible controller state | `mutableStateOf`, `mutableStateListOf`, or `mutableStateMapOf` with restricted setters |
| File/bitmap writes | Single-thread executor or serialized coroutine channel |
| Main-thread callback | Switch explicitly to main before touching UI-observed state |
| Shared singleton repository | `@Volatile` plus synchronized lazy creation using `applicationContext` |
| Async callback | Capture immutable identity/snapshot and validate it before applying |

