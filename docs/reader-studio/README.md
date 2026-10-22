# Reader Studio

## Topic lookup

| Need | Detail | Main code |
| --- | --- | --- |
| DOM extraction, sanitization, request/session identity | [`extraction-and-session.md`](extraction-and-session.md) | `ReaderExtraction`, `ReaderModels`, `ReaderStudioSession` |
| Library, offline snapshots, settings, speech, Compose UI and motion | [`library-speech-and-ui.md`](library-speech-and-ui.md) | `ReaderLibrary*`, `ReaderSpeech`, `ReaderStudioScreen` |
| Android entry, Library/Feeds tabs, source chooser and unread inbox | [`../rss/README.md`](../rss/README.md) | `ReaderStudioHomeHost`, `ReaderStudioHomeScreen`, `RssInbox*` |
| RSS article handoff into Reader Studio | [`../rss/README.md`](../rss/README.md) | `IncomingLinkRouter.openInReader`, RSS activity, feed widget and notifications |
| Shared website/feed collection and browser entry | [`../feeds/README.md`](../feeds/README.md) | `FeedsHost`, `FeedReaderDataSource`, `FeedsScreen` |

## Test lookup

| Surface | Tests |
| --- | --- |
| Extraction contract/parser | `ReaderExtractionContractTest`, `ReaderExtractionParserInstrumentedTest` |
| Session, library, speech rules | `reader/*RulesTest` |
| Offline speech choice, lifecycle, model integrity and inference | `ReaderSpeechControllerInstrumentedTest`, `OfflineSpeechModelFilesInstrumentedTest`, opt-in `OfflineSpeechEngineInstrumentedTest` |
| Voice choice and model settings | `ReaderSpeechOptionsInstrumentedTest` |
| Nano opt-in, download, fallback, cancellation and original offsets | `ReaderSpeechPreparationInstrumentedTest`, `ReaderSpeechPreparationRulesTest` |
| Nano availability, adult confirmation and settings | `ReaderSpeechPreparationOptionsInstrumentedTest` |
| Persistence | `ReaderLibraryStoreInstrumentedTest` |
| Compose UI and inbox navigation | `ReaderStudioScreenInstrumentedTest`, `ReaderStudioHomeScreenInstrumentedTest`, `BrowserRootBackInstrumentedTest` |
| RSS read state and article loading | `RssReadStoreInstrumentedTest`, `RssInboxRulesTest`, `RssReaderLoadRulesTest` |

