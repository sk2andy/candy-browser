# History

## Ownership

| Layer | Responsibility | Main code |
| --- | --- | --- |
| Model and policy | Canonicalization, per-profile deduplication, filtering, grouping and deletion | `data/BrowsingLibrary.kt` |
| Mutation owner | Atomic record, delete, clear and recording-mode changes across activities | `data/BrowsingHistoryRepository.kt` |
| Persistence | Backward-compatible JSON rows and recording mode | `data/BrowserSessionStore.kt` |
| Full-text Recall | Profile-isolated readable-page index, lexical ranking and matching excerpts | `recall/RecallModels.kt`, `data/RecallRepository.kt` |
| Browser integration | Record committed regular-page visits and open an entry in its owning profile | `browser/BrowserController.kt` |
| Activity and UI | Search, date groups, multi-profile selection and history controls | `HistoryActivity.kt`, `ui/HistoryScreen.kt` |
| App lifecycle | Apply clear-on-exit only after every Candy activity leaves the foreground | `BrowsingHistoryLifecycle.kt` |

## Behavior

- History is a separate, non-exported activity opened from the browser `…` menu immediately before Settings.
- It starts with the active profile selected. Profile chips can combine any set of enabled regular profiles; an entry keeps its owning profile emoji when multiple profiles are visible.
- Search matches page title, host and URL. When Candy Recall is enabled, it also returns selected-profile full-text matches with bounded matching excerpts. Entries remain profile-isolated, ordered newest first and grouped by local calendar date.
- Selecting row checkboxes enables bulk deletion. Clear opens an inclusive **Since** / **Until** local date-and-time range and profile multi-selector. The end minute is included. It removes matching history from the latest stored snapshot, independent of the current search query, and rebuilds matching Candy Trails from their retained nodes.
- Opening a row returns to `MainActivity`, validates the HTTP(S) URL and profile, switches to the owning profile when profiles are enabled, and opens the page in a regular tab.

## Persistence and privacy

- A canonical URL is retained once per profile with its latest title and visit time. The combined history remains bounded to 250 entries.
- Legacy rows without `profileId` migrate to the Candy profile. Malformed non-web rows remain excluded by history rules.
- Address suggestions and domain completion receive only the selected tab's profile history. One regular profile cannot reveal another profile's visits there.
- Private tabs and federated-login popup tabs never reach the repository. Link Peek remains
  read-only and does not record visits.
- Candy Recall is a separate disabled-by-default local index. It never indexes or searches private tabs or Link Peek. History search queries Recall only for the regular profiles selected on this screen.
- Disabling **Save history** stops new records without deleting existing entries.
- **Clear when Candy closes** deletes saved browsing-history rows and their matching Candy Trail nodes after the whole app leaves the foreground. Moving from `MainActivity` to `HistoryActivity` is not an exit.
- A persisted session marker clears history on the next cold start if the process ended before the normal foreground-exit callback could run.
- Range, clear-on-exit and profile deletion commit a persistent Candy Trail redaction together with history. Trail files are rewritten on their serialized executor; failed or interrupted work remains queued and retries on foreground/startup.
- Deleting a profile also deletes its history. Profile history is not reassigned to the fallback profile.
- Individual deletion, range deletion, clear-on-exit and profile deletion also redact matching Recall rows. Disabling Recall clears its full-text index without deleting ordinary History rows.
