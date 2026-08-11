# Reader Studio

## Topic lookup

| Need | Detail | Main code |
| --- | --- | --- |
| DOM extraction, sanitization, request/session identity | [`extraction-and-session.md`](extraction-and-session.md) | `ReaderExtraction`, `ReaderModels`, `ReaderStudioSession` |
| Library, offline snapshots, settings, speech, Compose UI | [`library-speech-and-ui.md`](library-speech-and-ui.md) | `ReaderLibrary*`, `ReaderSpeech`, `ReaderStudioScreen` |

## Test lookup

| Surface | Tests |
| --- | --- |
| Extraction contract/parser | `ReaderExtractionContractTest`, `ReaderExtractionParserInstrumentedTest` |
| Session, library, speech rules | `reader/*RulesTest` |
| Persistence | `ReaderLibraryStoreInstrumentedTest` |
| Compose UI | `ReaderStudioScreenInstrumentedTest` |

