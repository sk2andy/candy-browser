# Library, speech and UI

## Library

| Piece | Responsibility |
| --- | --- |
| `ReaderLibraryRules` | Settings, progress, snapshot ordering, private-mode no-op policy |
| `ReaderLibraryStore` | Bounded local encoding and storage budget; one process-wide lock serializes repository writes and independent document/search readers |
| `ReaderLibraryRepository` | Serialize I/O operations and return callbacks on main thread |
| `ReaderStudioScreen` | Shared Android/iOS typography, themes, alignment, save/delete, progress and speech controls |

| Invariant | Value |
| --- | --- |
| Saved snapshots | Newest 20 |
| Font scale | 0.8–1.6 |
| Reading progress | 0–1 |
| Private reader state | Fresh in-memory state; no settings, progress or snapshots persisted |
| Android document picker | Newly saved regular snapshots record their source profile. Unprotected local profiles can expose those snapshots as offline PDFs through [Candy's document provider](../browsing/documents-provider.md); legacy snapshots without provenance stay within Candy. |

Link Peek can extract the fully loaded, committed preview WebView and save its sanitized reader
document directly for offline reading. The extraction remains bound to the preview WebView,
content revision, committed URL and source tab; stale results are rejected. Private previews never
write reader snapshots.

The configurable Android address-bar long press can save the selected page through the same
repository without opening Reader Studio. Its extraction is bound to the selected tab, committed URL,
navigation generation and engine session; the sanitized document URL must still match the requested
page before persistence. Private tabs never expose this action.

## Speech

| Piece | Responsibility |
| --- | --- |
| `ReaderSpeechRules` | Pure status reducer and current excerpt selection |
| `ReaderSpeechController` | Android `TextToSpeech`, chunk queue, range callbacks and cleanup |
| `ReaderDocument.speechText` | Stable title+block text projection |

The iOS host maps its tab/session-bound WebKit extraction into `ReaderExtractionResult` and renders this
same screen. Its current library is session-memory-only and its speech adapter reports unavailable; no
parallel SwiftUI Reader surface exists.

- Drive state transitions through `ReaderSpeechEvent`; keep Android callbacks out of reducer logic.
- Close speech controller with screen/session lifecycle.
- Test reducer/library behavior on JVM; test store, TTS wiring and Compose semantics only where platform behavior matters.

Android selected-text actions reuse `ReaderSpeechController` for temporary read-aloud sessions without creating
Reader documents or library snapshots. These sessions stop when their activity leaves the foreground and
close their speech engine on destruction. See [`selected-text-actions.md`](../browsing/selected-text-actions.md).
