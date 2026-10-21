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
| `ReaderSpeechController` | Shared first-use voice choice, backend routing, pending-play cancellation and cleanup |
| `AndroidReaderSpeechController` | Android `TextToSpeech`, chunk queue and range callbacks |
| `OfflineReaderSpeechController`, `OfflineSpeechEngine` | Bounded sentence synthesis with Supertonic 3 / ONNX Runtime and memory-only PCM playback |
| `ReaderSpeechModelRepository`, `OfflineSpeechModelFiles` | App-wide voice preference; explicit, pinned, integrity-checked model download and deletion |
| `ReaderDocument.speechText` | Stable title+block text projection |

The iOS host maps its tab/session-bound WebKit extraction into `ReaderExtractionResult` and renders this
same screen. Its current library is session-memory-only and its speech adapter reports unavailable; no
parallel SwiftUI Reader surface exists.

- Drive state transitions through `ReaderSpeechEvent`; keep Android callbacks out of reducer logic.
- Close speech controller with screen/session lifecycle.
- Test reducer/library behavior on JVM; test store, TTS wiring and Compose semantics only where platform behavior matters.

| Offline speech behavior | Contract |
| --- | --- |
| First request | Reader and selected-text speech share one choice: download the local model or use Android voices. Dismissal cancels playback without recording a choice. |
| Download | Explicit public model download, about 145 MB; never sends article text. Pinned individual assets are size/SHA-256 checked before use; partial files are cleaned up. |
| Settings | Browser → Read aloud offers download, delete and enable/disable. Disabling preserves files and uses Android voices. Deleting disables local speech and removes model assets. |
| Private content | Text and generated audio stay in memory. Only the app-wide voice choice and public model files persist; model files are excluded from backup. |
| Playback | Language-agnostic multilingual input, bounded sentence chunks with one sentence prepared ahead of playback; pause resumes from the current sentence. No word-level timing is fabricated. |
| Lifecycle | Leaving the screen or backgrounding cancels pending playback and active audio. Download completion cannot restart a cancelled session. Native inference resources close after playback. Audio-focus loss pauses speech. |
| License | Supertonic 3 model weights use OpenRAIL-M; reference inference code is MIT. Notices and license text ship with the app. |

Android selected-text actions reuse `ReaderSpeechController` for temporary read-aloud sessions without creating
Reader documents or library snapshots. These sessions stop when their activity leaves the foreground and
close their speech engine on destruction. See [`selected-text-actions.md`](../browsing/selected-text-actions.md).

## Reader controls

The article uses a flat surface behind the system status bar down to the navigation inset, with no
Reader Studio header or separate settings toolbar. Text has readable internal gutters and the last
paragraph can scroll above the floating controls. The large bottom pill displays the current article
URL as read-only text and an ellipsis settings action. Scrolling down accumulates 24 dp before
switching to a compact domain/settings pill; scrolling up accumulates 16 dp before restoring the full
URL pill, matching the browser thresholds. Direction changes reset that distance, and reaching the
article top expands the pill. Both sizes retain the same settings action; there is no eye button.
Saved-progress restoration and layout changes do not count as user scrolling. There is no URL editor or keyboard in Reader Studio; returning to the
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

Android wraps Reader Studio in the same default predictive Back shrink used by Settings. Gesture
progress drives scale, horizontal translation and corner rounding; cancellation restores the reader
without taking its Back action. Commit retains the shared restore/library/dismiss order above.
The settings bottom sheet continues to handle its own Back gesture before the reader surface.


Paper is the default for new, missing, or invalid theme settings; explicit saved choices are retained.
Light remains bright regardless of the browser/system dark appearance. The article has no origin chip
above its title; the read-only floating pill is its sole URL display. Initial content starts below the
status icons using a spacer inside the scroll content. This spacer scrolls away, so text moves behind
the status bar. A theme-colored status scrim and matching system-icon appearance preserve contrast;
horizontal cutouts and the bottom navigation inset still protect controls. The offline library keeps
its status-bar inset and enough trailing space to scroll its final entry above the pill.

## Motion and save feedback

| Surface | Behavior | State boundary |
| --- | --- | --- |
| Android reader entry | Short fade with an 8 dp upward settle; controls remain immediately available | No delayed dismissal or extra reader session |
| Extraction | Article gently fades in after loading | No additional extraction or repository operation |
| Offline library | Short horizontal transition; Back reverses direction | Article and library retain their scroll positions; restored progress is not reapplied on return |
| Save offline | Download icon crossfades to a checkmark after the repository callback returns the matching document snapshot; one small pulse acknowledges completion | Empty, evicted, private, or mismatching snapshots never confirm a save; no new persistence |
| Reduced motion | iOS Reduce Motion skips content/icon transitions and pulse; Android uses the existing animation duration policy | Saved state and navigation remain available with animations disabled |

Only the current destination remains composed during navigation; outgoing content cannot intercept touches or accessibility actions. The save checkmark also
exposes the localized offline state on its button. Returning to settings retains the checkmark without
starting another save. A save completed while the library is open still updates its list. Pulse feedback remains bound to the article; closing or replacing the Reader session rejects its stale library callback.

## Offline speech model provenance

| Asset or contract | Source / invariant |
| --- | --- |
| Inference | Supertonic 3, existing ONNX Runtime Android 1.24.3 CPU provider; no separate native runtime |
| Reference algorithm | [Supertone Java example](https://github.com/supertone-oss-archive/supertonic/tree/main/java), MIT; notice included in both app variants |
| Weights | [sherpa-onnx INT8 conversion](https://huggingface.co/csukuangfj2/sherpa-onnx-supertonic-3-tts-int8-2026-05-11), revision `cca5a0e6c96e1d2c720986bf7e75fcc81dee3ae4` |
| Model license | [Open RAIL-M](https://huggingface.co/supertone-oss-archive/supertonic-3/blob/aafc6e32416a594460b32413efc49d7fe4ce6d46/LICENSE), bundled and downloaded; distinct from the reference code's MIT license |
| Transfer | 145,310,775 bytes across individually pinned files, including license; `.part` files never count as ready |
| Audio | 44,100 Hz mono float PCM, matching the pinned model configuration; first preset F1 |
| Text | At most 300 UTF-16 characters per inference, NFKD normalization, language-agnostic `<na>` tags for local multilingual synthesis |
| Limits | Eight denoising steps, two CPU inference threads, bounded 45-second prediction per chunk; cancellation checked between stages and steps |
| Verification | `OfflineSpeechModelFilesTest`, `OfflineSpeechTextRulesTest`, `OfflineSpeechModelFilesInstrumentedTest`; optional `OfflineSpeechEngineInstrumentedTest` requires preloaded assets and `offlineSpeechModelDirectory` instrumentation argument |
