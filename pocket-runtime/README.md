# Pocket TTS Android runtime

| Piece | Contract |
| --- | --- |
| `NativePocketSpeech` | Java/JNI boundary; create, synthesize and destroy serially on the owning speech worker |
| `PocketSpeechEngine` in `app` | Existing `LocalSpeechEngine` contract; bounded UTF-8 input and finite 24 kHz mono PCM output |
| Model lease | The app owns downloaded, verified language-pack directories and prevents deletion while inference uses them |
| Privacy | No HTTP server, audio file output, voice embedding disk cache, or article logging; text and PCM remain in memory |
| Cancellation | Checks before voice conditioning, after conditioning, between autoregressive frames and before PCM callbacks; an in-flight ONNX call must finish before cancellation returns |
| Limits | At most 1,024 UTF-16 characters / 4,096 UTF-8 bytes per call; at most 120 seconds of generated PCM; app normally supplies smaller sentence chunks |
| Native memory | Sessions are destroyed with the owning engine; no process-wide model cache; RNG state is thread-local |
| Build | NDK `27.2.12479018`, CMake `3.22.1`; shared ONNX Runtime AAR `1.24.3` provides matching headers and native library; 16 KiB page support enabled |

## Pinned sources

| Component | Revision / integrity | License |
| --- | --- | --- |
| PocketTTS.cpp | Android compatibility snapshot from `The-unknown-Shadowman/PocketTTS-Android-Engine` at `dd35a2635043f7263c0e315b80599b37cefacd19`, originally based on `VolgaGerm/PocketTTS.cpp` at `e801e7d6c2692121a39e80ae525cb5265174a495` | MIT |
| SentencePiece | `google/sentencepiece` tag `v0.2.1`, source archive SHA-256 `c1a59e9259c9653ad0ade653dadff074cd31f0a6ff2a11316f67bee4189a8f1b` | Apache-2.0, bundled dependency notices retained |
| dr_libs | `mackron/dr_libs` commit `50bb723e6a459dbb781e26cefee4fd9ca6714d6a`, archive SHA-256 `ebf2a141056247b4c022c513adcb162ac61c43ba3fc2f7b60153db819b5b9bad` | MIT-0 / public domain |
| ONNX Runtime | Maven `com.microsoft.onnxruntime:onnxruntime-android:1.24.3` | MIT |

CMake downloads the two checksum-pinned source archives on the first native build. Model weights are
not build dependencies and are never included in the APK. The app downloads them only after explicit
user action. The runtime accepts the five regular FP32 language packs pinned by the app catalog;
24-layer models and arbitrary imported model graphs are not supported.

## Changes from the upstream snapshot

| Change | Reason |
| --- | --- |
| Removed HTTP server, CLI and asynchronous C API | Candy needs only in-process inference |
| Replaced generator/decoder worker queues with serial, bounded generation | Propagate native exceptions, avoid unbounded queues and make cancellation ownership explicit |
| Require EOS and completion of its trailing frames | Reject generation-budget exhaustion instead of accepting a silently truncated sentence |
| Disabled disk caches and verbose logs | Keep article sessions transient, including private-mode playback |
| Thread-local random generator | Independent speech workers do not race over mutable global RNG state |
| JNI accepts UTF-8 bytes | Preserve supplementary Unicode characters rather than JNI modified UTF-8 |
| Generic native error boundary | No article or model details escape through native failure logs |

Runtime license texts ship in `app/src/main/assets/licenses/pocket-speech-runtime.txt`.
Model and bundled-voice licenses are separate from the runtime software licenses.
