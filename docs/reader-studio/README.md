# Reader Studio

## Topic lookup

| Need | Detail | Main code |
| --- | --- | --- |
| DOM extraction, sanitization, request/session identity | [`extraction-and-session.md`](extraction-and-session.md) | `ReaderExtraction`, `ReaderModels`, `ReaderStudioSession` |
| Library, offline snapshots, settings, speech, Compose UI and motion | [`library-speech-and-ui.md`](library-speech-and-ui.md) | `ReaderLibrary*`, `ReaderSpeech`, `ReaderStudioScreen` |
| RSS article handoff into Reader Studio | [`../rss/README.md`](../rss/README.md) | `IncomingLinkRouter.openInReader`, RSS activity, feed widget and notifications |

## Test lookup

| Surface | Tests |
| --- | --- |
| Extraction contract/parser | `ReaderExtractionContractTest`, `ReaderExtractionParserInstrumentedTest` |
| Session, library, speech rules | `reader/*RulesTest` |
| Offline speech choice, lifecycle, model integrity and inference | `ReaderSpeechControllerInstrumentedTest`, `OfflineSpeechModelFilesInstrumentedTest`, opt-in `OfflineSpeechEngineInstrumentedTest` |
| Voice choice and model settings | `ReaderSpeechOptionsInstrumentedTest` |
| Persistence | `ReaderLibraryStoreInstrumentedTest` |
| Compose UI | `ReaderStudioScreenInstrumentedTest` |

