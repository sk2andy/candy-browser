# App logging

## User flow

| Step | Action |
| --- | --- |
| Unlock | Long-press About & legal in Settings to reveal Developer options |
| Capture | Enable App logging under Diagnostics, then reproduce the problem |
| After a crash | Reopen Candy; local logs survive a process restart |
| Export | Export app logs opens Android's document picker and saves `candy-app-logs.txt`; attach this file to the bug report |
| Clean up | Delete app logs clears the capture while logging continues; switching logging off stops capture and deletes local logs |

## Privacy and storage contract

| Boundary | Contract |
| --- | --- |
| Default | Disabled in both Full and FOSS builds; no upload or additional permissions |
| Capture | Fixed app startup, extension installation/read/mutation and renderer termination events; uncaught JVM exceptions include bounded error classes and code frames; Android native crash history adds the crashed thread's stack |
| Excluded data | No exception messages, source filenames, URLs, page content, titles, inputs, extension IDs, profile names, or thread names |
| Private tabs | Any open private tab pauses logging across controllers; entering private mode invalidates queued writes; individual engine callbacks also reject private sessions |
| Retention | Two rotating log files, each at most 256 KiB; each record at most 16 KiB; bounded background queue drops excess events; small separate capture cutoff/revocation metadata remains when logs are deleted |
| Crash write | Synchronous bounded write with file-descriptor sync before delegating the unchanged exception to Android's existing handler |
| Process scope | Main process writes logs; native history covers the main process and the explicit Gecko engine-process allowlist; engine-restart and app-data-transfer processes are excluded |
| Storage | `noBackupFilesDir/app_logs` plus local revocation/cutoff preferences in `shared_prefs/app_logging_capture.xml`; logs, capture preferences and their `.xml.bak` copy are excluded from Android backup/transfer and Candy app-data archive export/import |
| Export | Explicit document-picker action; includes app/engine versions, SDK, device manufacturer/model and current diagnostic status; pending writes complete before snapshot |
| Exported copies | User-owned files remain after deleting local logs or switching capture off |
| Cleanup failure | Failed deletion is reported; failures to capture diagnostics do not prevent normal crash handling |

## Ownership and limits

| Concern | Owner / limit |
| --- | --- |
| Payload and bounds | `data/AppLogRules`, `data/AppLogStore`; deterministic JVM tests |
| Android capture/export | `data/AppLogging`, `CandyApplication`, `MainActivity` |
| Developer control | `DeveloperSettings`, `BrowserSessionStore`, `DeveloperOptionsSettingsPage` |
| Engine/extension events | `GeckoExtensionRepository`, `GeckoViewRuntimeHandle`, `SystemWebViewBrowserEngineAdapter` |
| Native engine crash | `ApplicationExitInfo` tombstone protobuf is checked after restart, renderer termination and during export; only native-crash records from authorized capture intervals are accepted |
| Native stack content | At most 64 frames of the crashed thread: architecture, signal/code, relative PC, library basename, optional code symbol/offset and module Build-ID; no registers, memory, abort text, paths, thread names, file descriptors or system log buffers |
| Native privacy boundary | Entering private mode closes the capture interval before private tabs are created; resuming creates a fresh interval. Clearing/off advances the durable cutoff. Revocation in separate preferences protects against a stale checkpoint after failed file replacement; clock rollback blocks capture until a new forward-time reset |
| Native availability | Android's global tombstone ring may overwrite or omit traces; malformed/oversized traces are rejected. No raw minidumps or uploads. Relative PCs and Build-IDs support later symbolization with matching native symbols |
| System logs | Unrelated Logcat output, website console output and Gecko profiler samples are outside this app-managed capture |
| Release stack frames | Minified frames require the matching release mapping for interpretation |

## Verification lookup

| Surface | Tests |
| --- | --- |
| Payload privacy / stack bounds | `AppLogRulesTest` |
| Rotation / restart / stale writes | `AppLogStoreTest` |
| Crash handler delegation | `AppLogCrashHandlerTest` |
| Native trace privacy / protobuf bounds | `NativeCrashTraceRulesTest` |
| Native process/time admission | `NativeCrashCaptureRulesTest` |
| Native system integration | API 37 dedicated emulator: real `SIGABRT` in the main app and Gecko GPU process; sanitized native frames recovered after restart |
| Durable native capture gates / restart / corruption | `NativeCrashHistoryStoreInstrumentedTest` |
| Android export / private capture / deletion | `AppLoggingInstrumentedTest` |
| Toggle / export / delete actions | Focused `DeveloperOptionsSettingsPageInstrumentedTest` method |
| Setting persistence / corrupt values | Focused `BrowserSessionStoreInstrumentedTest` methods |
| Archive exclusion | `AppDataArchiveRulesTest`, `AppDataArchiveCodecTest` |
