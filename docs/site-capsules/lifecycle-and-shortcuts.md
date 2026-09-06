# Lifecycle and shortcuts

## Lifecycle lookup

| Stage | Source | Result |
| --- | --- | --- |
| Edit | `SiteCapsuleEditorActivity`, `SiteCapsuleEditorContract` | Bounded request/submission crossing an Android activity contract; icon emoji and accessible tile color restore into the live preview |
| Create/update | `BrowserController` + `SiteCapsuleRules` + `CapsuleIconUpdateRules` | Persist normalized Capsule-owned icon settings, resolve legacy source availability, and re-render the icon/shortcut |
| Launch | `CapsuleIntentRules` → `MainActivity` | Open known opaque capsule ID or fall back to normal home |
| Runtime | `BrowserController` + `SiteCapsuleScreen` | Bind one capsule/profile/tab and apply capsule navigation/chrome |
| Delete | `CapsuleDeletionRules` | Disable shortcut; delete dedicated profile only after confirmation and last-owner check |

## Storage and shortcut bounds

| Data | Storage/bound |
| --- | --- |
| Capsule list | Atomic versioned JSON, max 512 KiB, max 64 records |
| Rendered icon and source favicon | Separate atomic PNGs, each max 256×256 and 256 KiB; source retention lets color changes re-render favicon shortcuts |
| Shortcut label | Projection caps short label at 40 characters |
| Launch identity | Stable shortcut prefix plus validated capsule ID |

Legacy records may have only a rendered favicon icon. The editor preserves that bitmap unchanged while icon customization stays unchanged, never stores it as a source favicon, and switches explicitly to the Capsule emoji fallback when emoji or tile color changes. This prevents recursively embedding an already rendered tile.

## Main files

| Concern | File |
| --- | --- |
| Records | [`SiteCapsuleStore.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/data/SiteCapsuleStore.kt) |
| Icons | [`SiteCapsuleIconStore.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/data/SiteCapsuleIconStore.kt) |
| Shortcuts | [`CapsuleShortcutPublisher.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/capsule/CapsuleShortcutPublisher.kt) |
| Deletion | [`CapsuleDeletionRules.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/capsule/CapsuleDeletionRules.kt) |
