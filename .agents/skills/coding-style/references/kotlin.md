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

## Extensions and persistence helpers

- Use an extension when an operation primarily acts on its receiver. Keep it beside its clients and restrict visibility as far as practical; this follows Kotlin's official extension guidance.
- Parse external IDs and path components into a trusted form before constructing a file path. Return the established nullable/typed rejection outcome for malformed input.
- Extract a small stateful helper when multiple stores share one configured invariant such as directory, filename validation and pruning. Keep caller-specific policy in each store.
- For `AtomicFile`, pair every `startWrite()` with `finishWrite()` on success or `failWrite()` on failure. Never close the returned stream directly; serialize access outside `AtomicFile`.
- Check resolved AndroidX Core before adding a wrapper. `androidx.core.util.tryWrite` exists from Core 1.19.0; use it only when available and its throwing contract fits the store API.
- Separate rollback from failure translation. Roll back every failed write; translate only expected encoding/I/O failures when the store contract is explicitly best-effort. Rethrow cancellation and do not broaden catches during extraction.
- Preserve lifecycle differences during shared-mechanics refactors: clearing contents and removing a directory are distinct operations and should have distinct names.

## Documentation basis

| Guidance | Primary source |
| --- | --- |
| Expression bodies, extension placement and narrow visibility | [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html) |
| Data-layer boundaries and testing | [Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations) |
| Atomic write lifecycle and external synchronization | [`android.util.AtomicFile`](https://developer.android.com/reference/android/util/AtomicFile) |
| Versioned Kotlin atomic-write helper | [`androidx.core.util.AtomicFileKt`](https://developer.android.com/reference/androidx/core/util/AtomicFileKt) |

## State and concurrency

| Case | Pattern |
| --- | --- |
| Compose-visible controller state | `mutableStateOf`, `mutableStateListOf`, or `mutableStateMapOf` with restricted setters |
| File/bitmap writes | Single-thread executor or serialized coroutine channel |
| Main-thread callback | Switch explicitly to main before touching UI-observed state |
| Shared singleton repository | `@Volatile` plus synchronized lazy creation using `applicationContext` |
| Async callback | Capture immutable identity/snapshot and validate it before applying |
