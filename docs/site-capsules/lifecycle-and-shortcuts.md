# Lifecycle and shortcuts

## Lifecycle lookup

| Stage | Source | Result |
| --- | --- | --- |
| Edit | `SiteCapsuleEditorActivity`, `SiteCapsuleEditorContract` | Bounded request/submission crossing an Android activity contract |
| Create/update | `BrowserController` + `SiteCapsuleRules` | Persist normalized capsule and refresh icon/shortcut |
| Launch | `CapsuleIntentRules` → `MainActivity` | Open known opaque capsule ID or fall back to normal home |
| Runtime | `BrowserController` + `SiteCapsuleScreen` | Bind one capsule/profile/tab and apply capsule navigation/chrome |
| Delete | `CapsuleDeletionRules` | Disable shortcut; delete dedicated profile only after confirmation and last-owner check |

## Storage and shortcut bounds

| Data | Storage/bound |
| --- | --- |
| Capsule list | Atomic versioned JSON, max 512 KiB, max 64 records |
| Icon | Atomic PNG, max 256×256 and 256 KiB |
| Shortcut label | Projection caps short label at 40 characters |
| Launch identity | Stable shortcut prefix plus validated capsule ID |

## Main files

| Concern | File |
| --- | --- |
| Records | [`SiteCapsuleStore.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/data/SiteCapsuleStore.kt) |
| Icons | [`SiteCapsuleIconStore.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/data/SiteCapsuleIconStore.kt) |
| Shortcuts | [`CapsuleShortcutPublisher.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/capsule/CapsuleShortcutPublisher.kt) |
| Deletion | [`CapsuleDeletionRules.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/capsule/CapsuleDeletionRules.kt) |

