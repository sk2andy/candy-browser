# Previews, snoozing and isolation

## Previews

| Piece | Responsibility |
| --- | --- |
| `TabPreviewCaptureRules` | Bound capture geometry and reject likely failed PixelCopy results |
| `BrowserController` | Own PixelCopy/WebView timing, reject captures through transparent view hierarchies, and enforce navigation generation/stale-result checks |
| `TabPreviewRepository` | Serialize preview file I/O on one executor and prune unknown tab IDs |
| `TabPreviewStore` | Validate bitmap dimensions/encoding and bound stored data |
| `AtomicTabFileDirectory` | Share safe UUID filenames, atomic writes, pruning and explicit directory lifecycle with favicon and WebView-state stores |

## Snoozing

| Piece | Responsibility |
| --- | --- |
| `SnoozeTimeRules` | Convert presets/custom local times to wake instants |
| `SnoozeRules` | Permit only future, non-incognito snoozes |
| `SnoozeMutationRules` / `SnoozeUndoRules` / `SnoozeRestoreRules` | Pure reschedule, undo and due-tab restore behavior |
| `BrowserSessionStore.saveTabsAndSnoozedImmediately` | Commit active+snoozed snapshot together and roll back on failure |
| `SnoozeScheduler` / `SnoozeWakeNotifier` | Android alarm and notification edges |

## Profiles and WebView storage

| Case | `WebViewProfileRules` assignment |
| --- | --- |
| Unsupported provider or regular shared profile | `Default` |
| Private tab with provider support | Dedicated incognito WebView profile |
| Isolation-enabled regular profile with provider support | Deterministic isolated WebView profile |

- Recreate affected WebViews when their storage assignment changes.
- Delay deletion of provider storage while WebViews still reference it; retry pending deletions.
- Move/delete tabs and side data as one controller operation; preserve private/non-private boundary.
