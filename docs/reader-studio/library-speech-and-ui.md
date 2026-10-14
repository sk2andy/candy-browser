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

## Reader controls

The article uses a flat surface behind the system status bar down to the navigation inset, with no
Reader Studio header or separate settings toolbar. Text has readable internal gutters and the last
paragraph can scroll above the floating controls. The large bottom pill displays the current article
URL as read-only text, an eye action, and a settings action. The eye alternates between this pill and
a compact eye/settings pill. There is no URL editor or keyboard in Reader Studio; returning to the
original closes the reader before browser navigation.

The Material bottom sheet contains font size, serif/sans typeface, alignment, system/light/paper/night
colors, offline saving and library access, progress, and speech transport. Settings apply immediately;
dismissing the sheet by Back, scrim, swipe, or Continue reading keeps the article open. Typeface is
stored compatibly with older settings (missing/unknown font falls back to Sans). Private settings
remain in memory.

Hide controls completely removes both pills. Android Back first restores the compact pill; another
Back closes the reader. Ordinary scrolling and taps on article text never restore controls. Pill and
sheet state survive configuration restoration for the same source/private session. Android handles
Back through the shared screen's platform callback. Hosts without a Back callback, including iOS,
show an inset-aware Back button with the same restore, library, and dismissal behavior.


Paper is the default for new, missing, or invalid theme settings; explicit saved choices are retained.
Light remains bright regardless of the browser/system dark appearance. The article has no origin chip
above its title; the read-only floating pill is its sole URL display. Initial content starts below the
status icons using a spacer inside the scroll content. This spacer scrolls away, so text moves behind
the status bar. A theme-colored status scrim and matching system-icon appearance preserve contrast;
horizontal cutouts and the bottom navigation inset still protect controls. The offline library keeps
its status-bar inset and enough trailing space to scroll its final entry above the pill.
