# Library, speech and UI

## Library

| Piece | Responsibility |
| --- | --- |
| `ReaderLibraryRules` | Settings, progress, snapshot ordering, private-mode no-op policy |
| `ReaderLibraryStore` | Bounded local encoding and storage budget |
| `ReaderLibraryRepository` | Serialize I/O operations and return callbacks on main thread |
| `ReaderStudioScreen` | Typography, themes, alignment, save/delete, progress and speech controls |

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

- Drive state transitions through `ReaderSpeechEvent`; keep Android callbacks out of reducer logic.
- Close speech controller with screen/session lifecycle.
- Test reducer/library behavior on JVM; test store, TTS wiring and Compose semantics only where platform behavior matters.

