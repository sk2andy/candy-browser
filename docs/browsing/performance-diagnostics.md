# Gecko performance diagnostics

## Build and privacy contract

| Boundary | Contract |
| --- | --- |
| Normal APK | `candy.performanceDiagnostics` defaults to `false`; provider disabled, shell profiling disabled, no samples or timing entries |
| Diagnostic APK | Explicit `-Pcandy.performanceDiagnostics=true`; use signed, minified FullRelease for realistic reproduction |
| Activation | Never automatic; ADB shell starts a capture after Gecko runtime exists |
| Private browsing | Any open private Gecko session rejects capture; opening one cancels the entire capture and removes unpublished/exported files |
| Asynchronous completion | Generation check prevents canceled or stale results becoming readable |
| Profiler storage | One gzip JSON in app cache, no network upload; old diagnostic files removed on process initialization and before each capture |
| Profiler limits | Automatic stop after 120 seconds; reject compressed profiles over 64 MiB |
| Export access | Fixed read-only content URI, explicit Android `DUMP` permission on every provider operation; ordinary apps cannot read/control captures |
| Sensitive data | Gecko stacks/profiles can contain normal-page URLs; treat exports as sensitive and share only after inspection |
| Behavior | Live Blur, autoplay settings, rendering backend and Gecko style-thread count remain unchanged |

## Evidence ownership

### Manual native-inset DOM probe

| Boundary | Contract |
| --- | --- |
| Request | Diagnostic builds only; same DUMP-gated provider, fixed `dom-probe` command without arguments, code or selectors |
| Target | Exactly one active regular Gecko session with an attached view and a settled document; ambiguity rejects the request |
| Privacy/lifecycle | Any private Gecko session blocks and erases the memory-only result; navigation, deactivation or closure invalidates pending and ready results |
| Payload | Native allowlist: finite numeric env/viewport/geometry values, fixed tag/position enums and booleans; at most16 candidates,24 KiB; no page text, URLs, IDs, classes or arbitrary stylesheet content |
| Sampling | One explicit snapshot; a hidden fixed contained measurement node reads computed `env()` padding and is removed in `finally`; no observers or scroll work |
| Request identity | Start returns `queued` and `domCommandId`, never an old payload. Poll until that command ID is accepted and `domResultId` matches it; `busy` rejects an overlapping command |
| Native consistency | Native insets/view generation is captured before dispatch and rechecked after response; changed insets or view size reject the result, rather than mixing two configurations |
| Interpretation | Positive env values prove delivery, not author header/body adoption. Computed-style/rectangle reads can flush layout; this is not an unaffected CPU or checkerboard measurement |
| Layout | The probe itself adds no repair; normal Gecko CSS protection and retained fallback keep their independent configuration |

```sh
adb -s SERIAL shell content call --uri content://dev.sk2andy.materialbrowser.performance --method dom-probe
adb -s SERIAL shell content call --uri content://dev.sk2andy.materialbrowser.performance --method dom-probe-status
# Require the returned domCommandId to be accepted, domResultId to match it, and domStatus=ready.
# domPayload is the bounded JSON snapshot. discard erases it.
```

`accepted=true` means the command was queued, not that a probe succeeded. The result is never
written to app storage or uploaded. An explicit snapshot can affect author mutation observers;
do not compare scroll performance during sampling.

| Signal | Question it answers | Limitation |
| --- | --- | --- |
| Gecko `js`, `stackwalk`, `cpuallthreads`, `processcpu` | Which JS/native stacks and engine threads consume CPU? | Sampling adds overhead; Java sampling intentionally excluded because Gecko 155 races marker storage against teardown |
| Selected GeckoMain, StyleThread, Renderer, Compositor, WRRenderBackend, Media, Video, AndroidUI/main threads | Content/style/render/media attribution | Verify actual thread/sample coverage in the exported profile |
| Android `Candy.Diagnostics.Start/Stop/UserObservedGap`, page UserTiming gap mark | Where does the user-observed drawing gap occur? | Gap is a manual observation, not automatic detection; page mark requires an active content bridge |
| Android `Candy.Blur.Bind/Configure/Draw/Release`, hardware/software controller draw, hardware capture spans | Blur setup, controller draw and actual hardware source RenderNode recording separately | Hardware capture includes child drawing, not exclusive readback; GPU execution/fences remain outside these spans |
| Android `Candy.Blur.SourceChildDraw` | Ordinary child drawing on a software canvas | This source operation alone is neither snapshot recording nor blur; any caller's software BlurView fallback is measured in `SoftwareDraw` |
| Android `Candy.Blur.StateCoverage`, attached/enabled view and source counts | Explicit participant state, including zero counts when Clear has no attached blur sources | Missing coverage is not evidence of disabled blur; participant counts are not a global theme flag |
| Android `Candy.Blur.ConfigurationCoverage`, configuration/requested-configuration counts | Composed chrome intent, including Clear with no source/view participants | Coverage requires at least one composed chrome configuration; zero requested configurations means no covered chrome requests blur, not a last-writer global settings flag |
| Android `Candy.Blur.CaptureUnpublishedSource` / `ReleasedControllerDraw` | Hardware recording from an unpublished source / controller drawing after release | Publication and release are lifecycle states, not global Blur-off proof; an outgoing Frosted source may be unpublished while another chrome still requests blur |
| Blur-off red flag | Sustained hardware `Capture` or controller `Draw` while configuration coverage is 1 and requested-configuration count is 0 | Verify the settled Clear interval, not merely one teardown frame; counts describe current composed chrome intent, not stale persisted preferences |
| Android `Candy.Blur.PreDrawObserved` | BlurView pre-draw phase occurred | Observation marker only, not the duration of the library's private geometry-update listener |
| Android `Candy.Gecko.*` | Touch/scroll callbacks, document metrics, insets, backend changes, paint milestones and media-session states | Paint milestones are not per-frame; media-session events do not describe every page video |
| Gecko DOM `Candy.SafeArea.Css.*` | Bounded CSS classification batches, metadata-only mutations and unsupported-overlap verification | Normal scroll performs no geometry queries; mutations remain interaction-gated by default. These inclusive spans are not exclusive CPU time or a full-DOM protection proof |
| System WebView DOM `Candy.SafeArea.*` | Reconcile, point discovery/chunks, known sticky/offset updates, quiet protection/verification and mutation work | Sticky bootstrap shares DiscoveryChunk with dense discovery; known nested/moving repair remains synchronous. Diagnostics add no scans/observers; full fresh coverage remains required |
| DOM timing overhead | One start mark and a two-argument `performance.measure(name, startMark)` publish the full synchronous phase through the current timestamp | Same phase names and native work coverage; only redundant end marks/cleanup are removed. Four timing API calls per successful phase, all costs remain included in CPU attribution |
| Mutation/known-offset attribution | Compare `Mutations`, `OwnedMutationImmediate`, `OwnedMutationFrame` and `KnownOffsets` spans with GeckoMain `Element.getBoundingClientRect` and pending style/layout-flush stacks | Unrelated feed work waits for quiet; direct related attributes, stylesheet/meta changes and child mutations inside owned headers retain immediate repair. Ancestor feed insertion coalesces in a frame. Background tabs can pause animation frames; a frame can still force layout. Inclusive spans and sample counts are not exclusive CPU totals |
| DOM ancestor-path attribution | Compare sampled `parentElementOrShadowHost` and candidate/collision traversal stacks under matched input | The full synchronous mutation callback shares one lazy read epoch across record classification and immediate repair; actual writes clear before and after author reactions, and callback completion/exception releases the epoch. Collections allocate on first read, not every write invalidation; local-result publication guards prevent reentrant cache pollution. This reduces allocation/getter overhead without reading the viewport during style-only classification, changing fresh layout requirements or delaying header repair; it does not remove native author-top flush costs |
| Priority/collision query costs | Compare `nextPriorityElement` and compact-wide peer checks under matched native input | Added and attribute roots alternate bounded task slots; each lane alternates recent new roots with FIFO cursor progress. Repeated changes retain the cursor and coalesce a fresh follow-up instead of restarting at the root. Completion transitions and traversal bookkeeping share the cooperative time budget. Invisible compact peers skip rectangle queries, but visible ancestors and existing geometry boundaries remain eligible |
| Prompt additions while scrolling | `Candy.SafeArea.AddedPriority` counts bounded added-only frame packets | New controls cannot wait through continuous scroll. Large attribute backlogs remain for quiet discovery; scroll replaces execution state without starving the queued wakeup. Fresh reads and candidate identity retries are not a global protection proof. Cooperative 4-ms/eight-element budgets cannot interrupt one atomic native query |
| DOM `Candy.ScrollMetrics.Publish` | Existing scroll-metric publication cost | UserTiming entries use fixed names and are cleared immediately after measurement |
| Concurrent Perfetto | CPU scheduling, frame timelines and native spans alongside the Gecko profile | Native spans appear only while an app trace and diagnostic capture are both active |

## Local ADB workflow

Use one explicit serial for every command. Do not change device rotation settings.

```sh
./gradlew assembleFullRelease -Pcandy.performanceDiagnostics=true
adb -s SERIAL shell content call --uri content://dev.sk2andy.materialbrowser.performance --method start
adb -s SERIAL shell content call --uri content://dev.sk2andy.materialbrowser.performance --method status
# Wait for status=recording, then reproduce scrolling while Perfetto is recording.
adb -s SERIAL shell content call --uri content://dev.sk2andy.materialbrowser.performance --method gap
adb -s SERIAL shell content call --uri content://dev.sk2andy.materialbrowser.performance --method stop
adb -s SERIAL shell content call --uri content://dev.sk2andy.materialbrowser.performance --method status
# Wait for status=ready before export. Substitute the application ID for suffixed builds.
adb -s SERIAL exec-out content read --uri content://dev.sk2andy.materialbrowser.performance/gecko-profile.json.gz > gecko-profile.json.gz
adb -s SERIAL shell content call --uri content://dev.sk2andy.materialbrowser.performance --method discard
```

| Provider response | Meaning |
| --- | --- |
| `accepted=true` | Recognized command queued on Android main thread; not proof capture/export has completed |
| `starting` / `recording` | Waiting for native-parent acknowledgement / capture active; child-process sample coverage still needs verification |
| `stopping` / `exporting` / `ready` | Native completion pending / IO pending / validated gzip readable |
| `private_session` / `runtime_unavailable` | Start rejected by privacy/runtime boundary |
| `error` / `cleanup_failed` / `profile_too_large` | Capture cannot be used; do not interpret missing samples as missing work |

## Optimization decision gate

| Hypothesis | Evidence required before changing behavior |
| --- | --- |
| Reddit autoplay drives rendering load | Video/media stacks and observed playback correlate with the bad interval; then compare existing autoplay-block policy in an otherwise identical run |
| Parallel styling hurts latency | CSS/style stack attribution and scheduling prove contention, not simply high combined CPU; reducing workers can increase wall-clock styling time |
| Candy DOM work dominates | Safe-area phase durations and sampled stacks overlap the stall; compare a focused change without removing Blur |
| Blur/GPU bottleneck | GPU/render/fence evidence and capture-compatible backend work; Android UI CPU alone cannot rule this in or out |

Change one variable per comparison, using the same page, scroll pattern and signed Release build. Keep an unprofiled run to estimate tracing overhead.

## Emulator scaling fixture

| Owner | Contract |
| --- | --- |
| `app/src/androidTest/assets/gecko_safe_area_scaling_fixture.html` | Identical page for Android instrumentation and minified Release; retained open-Shadow-DOM feed, nonleaf additions, virtualization, late sticky header and tiny fixed control |
| `GeckoSafeAreaScalingInstrumentedTest` | Diagnostic Debug geometry/coverage checks at 256/1024/4096 retained feed elements; 96 bidirectional programmatic scroll steps |
| `scripts/gecko_safe_area_scaling_server.mjs` | Loopback-only host server; serves the unchanged fixture and retains at most 100 reports, each bounded to 64 KiB |
| Release workflow | Start the host server, reverse its explicit port on the exclusively owned emulator, start diagnostics, navigate the Release app to the fixture and read `/reports` |
| Input parameters | `scale=256\|1024\|4096`, `steps=24\|96\|192`, `stepMs=100..1000`, `run=1`; 96 steps at 550 ms fits a typical 120-second capture, but verify recording remained active |
| Coverage | Require actual Reconcile and PointDiscovery measures, not just an available `PerformanceObserver`; missing phases are missing evidence, not free work |
| Geometry | Sticky containment naturally ends when its containing section leaves the viewport; inactive samples are not repair failures |
| Scope | Programmatic scrolling and settled geometry do not prove native APZ-fling continuity or absence of white drawing gaps; verify these separately with native input and render traces |

Phase timings are nested/inclusive; never sum them into total CPU. Compare query/pass counts across
size and duration independently from wall time and native restyle cost. Capture/export must preserve
the existing privacy and explicit-serial rules above.

## Gecko 155 sampler lifecycle

| Constraint | Implementation |
| --- | --- |
| Java marker teardown race | Do not enable `java` or call `ProfilerController.addMarker`; queued Gecko event markers can race `SamplingRunnable` becoming null |
| Native activity signal | Gecko's package-bound `org.mozilla.fenix.PROFILER_STATE_CHANGED` broadcast, `isActive` boolean |
| Signal authenticity | Dynamic `RECEIVER_NOT_EXPORTED`, app-scoped signature permission defined and requested, main-thread delivery |
| Asynchronous ordering | A new capture waits for native stop-result completion and stopped acknowledgement; canceled late starts cannot be exported |
| Version boundary | Verified against Gecko 155 API bytecode and [Mozilla 155.0.1 native source](https://raw.githubusercontent.com/mozilla-firefox/firefox/FIREFOX_155_0_1_RELEASE/tools/profiler/core/platform.cpp); recheck when upgrading Gecko |
