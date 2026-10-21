# Android selected-text actions

| User action | Behavior |
| --- | --- |
| Select text in a compatible Android app → Research in Candy | Show the selected phrase and require an explicit regular-profile choice |
| Choose a profile | Build a normal encoded search URL using the configured search engine and open a new regular tab in that profile |
| Select text → Read aloud with Candy | Open a temporary speech screen backed by the Reader speech controller; the first request offers the same optional offline model as Reader Studio |
| Select read-only page text inside Candy → Read aloud with Candy | Gecko and System WebView expose an explicit action, including in private tabs; launch transfers only a one-use token |
| Pause / resume / stop | Control the selected-text speech session |
| Leave speech screen | Stop playback; closing the activity shuts down the speech controller |
| Select read-only page text inside Candy → Highlight + note | Save a profile-owned quote and note; see [`web-annotations.md`](web-annotations.md) |
| Select read-only page text inside Candy → Translate here | Use the local Android translation service, with an explicit selected page-provider fallback; see [`page-translation.md`](page-translation.md) |

Candy's in-page actions extend the engine-owned native selection menu. Existing copy, select-all,
share and installed Android text actions remain available. The exported actions below serve text
selected in other apps.

## Boundaries

| Concern | Contract |
| --- | --- |
| Android integration | Two exported `ACTION_PROCESS_TEXT` activities, `CATEGORY_DEFAULT`, `text/plain`; availability also depends on the source app exposing Android text actions |
| Input | Reject null, blank and oversized text before conversion; strip control characters except tab/newline |
| Research bounds | At most 2,000 characters; text always becomes a search query, including URL-shaped or command-shaped selections |
| Speech bounds | At most 20,000 characters in both browser engines and Android text actions; uses Reader speech chunking and lifecycle cleanup |
| Profile choice | Only local, unprotected profiles; if profiles are disabled, only the active eligible profile appears; eligibility rechecked before launch and again before navigation |
| Private browsing | Explicit regular-profile choice starts a regular tab; no selected text enters a private-tab store |
| Browser speech handoff | Launch intent carries a random one-use token; selected text stays in a process-memory registry with eight-request bound and 60-second validity. Editable, password and collapsed page selections never expose the custom action. |
| Research handoff | Launch intent carries only a random one-use token; text remains in a process-memory registry with eight-request bound and 60-second validity |
| Stale handoff | Process death, expired/replayed token or changed target eligibility prevents navigation |
| Speech persistence | No Reader library entry, speech text preference, saved-instance text or selected-text file is written |
| Recreation and recents | Incoming text removed from the activity intent; text is not restored after activity recreation; recent-app screenshots disabled |
| Source app | Result remains cancelled; selected source text is never replaced |
| Background | Speech cannot auto-start while its activity is stopped; active speech stops on leaving |

## Ownership and verification

| Concern | Source / test |
| --- | --- |
| Bounds, search URL, allowed profiles, one-use handoff | `ProcessTextRules`, `ProcessTextResearchRequestStore`, `ProcessTextSpeechRequestStore`, `ProcessTextRulesTest`, `ProcessTextSpeechRequestStoreTest` |
| In-browser native selection | `GeckoWebTextSelectionDelegate`, `WebTextActionModeCallback`, `WebTextOperationsInstrumentedTest` |
| Android entry and lifecycle | `ResearchSelectedTextActivity`, `ReadSelectedTextActivity`, `ProcessTextResearchIntentHandler` |
| Profile selection and speech controls | `SelectedTextScreens`, `SelectedTextScreensInstrumentedTest` |
| Installed Android text-menu actions | `ProcessTextManifestInstrumentedTest` |
