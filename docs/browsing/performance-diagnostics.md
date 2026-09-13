# Gecko performance diagnostics

## Build and privacy contract

| Boundary | Contract |
| --- | --- |
| Normal APK | `candy.performanceDiagnostics` defaults to `false`; provider disabled, shell profiling disabled, no samples or timing entries |
| Diagnostic APK | Explicit `-Pcandy.performanceDiagnostics=true`; use signed, minified FullRelease for realistic reproduction |
| Activation | Never automatic; ADB shell starts a capture after Gecko runtime exists |
| Private browsing | Any open private Gecko session rejects capture; opening one cancels the entire capture and removes unpublished/exported files |
| Asynchronous completion | Generation check prevents canceled or stale results becoming readable |
| Local storage | One gzip JSON in app cache, no network upload; old diagnostic files removed on process initialization and before each capture |
| Limits | Automatic stop after 120 seconds; reject compressed profiles over 64 MiB |
| Export access | Fixed read-only content URI, explicit Android `DUMP` permission on every provider operation; ordinary apps cannot read/control captures |
| Sensitive data | Gecko stacks/profiles can contain normal-page URLs; treat exports as sensitive and share only after inspection |
| Behavior | Live Blur, autoplay settings, rendering backend and Gecko style-thread count remain unchanged |

## Evidence ownership

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
| DOM `Candy.SafeArea.*` | Reconcile, point discovery/chunks, known sticky/offset updates, quiet protection/verification and mutation work | Diagnostics add no scans/observers; protection's bounded priority registration and conservative verification remain actual work |
| Mutation/known-offset attribution | Compare `Mutations`, `OwnedMutationFrame` and `KnownOffsets` spans with GeckoMain `Element.getBoundingClientRect` and pending style/layout-flush stacks | Feed mutation callbacks defer global owned-layout rereads until quiet; direct owned/ancestor/descendant or stylesheet/meta repair coalesces in the next before-paint frame. Background tabs can pause animation frames; the frame can still force layout. Inclusive spans and sample counts are not exclusive CPU totals |
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
