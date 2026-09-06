# Library, speech and UI

## Library

| Piece | Responsibility |
| --- | --- |
| `ReaderLibraryRules` | Settings, progress, snapshot ordering, private-mode no-op policy |
| `ReaderLibraryStore` | Bounded local encoding and storage budget |
| `ReaderLibraryRepository` | Serialize I/O operations and return callbacks on main thread |
| `ReaderStudioScreen` | Shared Android/iOS typography, themes, alignment, save/delete, progress and speech controls |

| Invariant | Value |
| --- | --- |
| Saved snapshots | Newest 20 |
| Font scale | 0.8–1.6 |
| Reading progress | 0–1 |
| Private reader state | Fresh in-memory state; no settings, progress or snapshots persisted |

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
