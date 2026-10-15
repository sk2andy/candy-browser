# Lifecycle and shortcuts

## Lifecycle lookup

| Stage | Source | Result |
| --- | --- | --- |
| Edit | `SiteCapsuleEditorActivity`, `SiteCapsuleEditorContract` | Bounded request/submission crossing an Android activity contract; favicon, custom crop, icon emoji and accessible tile color restore into the live preview |
| Import/crop | `CapsuleCustomIconEditorActivity`, `CapsuleCustomIconProcessor`, `CapsuleIconCropRules` | Open `image/*` through the system document picker, reject invalid/oversized content, downsample off-main, then produce a bounded square crop with pan/pinch/zoom controls |
| Icon pack | `CapsuleIconPackPickerActivity`, `CapsuleIconPackRepository`, `CapsuleIconPackRules` | Discover compatible installed packs through targeted theme intents, read bounded `drawable.xml` or `appfilter.xml` catalogs from `res/xml`, `res/raw` or assets, search unique drawables, and render the selected resource off-main |
| Create/update | `BrowserController` + `SiteCapsuleRules` + `CapsuleIconUpdateRules` | Persist normalized Capsule-owned icon settings, resolve legacy source availability, and re-render the icon/shortcut |
| Launch | `CapsuleIntentRules` → `SiteCapsuleLauncherActivity` → `MainActivity` / `SiteCapsuleActivity` | Resolve current settings for a known opaque Capsule ID; optionally open its own Android task |
| Runtime | `BrowserController` + `SiteCapsuleScreen` | Bind one capsule/profile/tab and apply capsule navigation/chrome |
| Delete | `CapsuleDeletionRules` | Disable shortcut; delete dedicated profile only after confirmation and last-owner check |

## Storage and shortcut bounds

| Data | Storage/bound |
| --- | --- |
| Capsule list | Atomic versioned JSON, max 512 KiB, max 64 records |
| Rendered icon, source favicon and custom crop | Three separate atomic PNGs, each max 256×256 and 256 KiB; inactive sources remain available when users switch modes |
| Imported image | Encoded source max 32 MiB, dimensions max 32,768 px, decoded pixels max 67,108,864; decoder downsamples to max 2,048 px and rejects partial images |
| Custom crop IPC/output | PNG max 192×192 and 256 KiB; only the cropped result crosses activity contracts |
| Icon-pack catalog | XML assets max 8 MiB, parser max 100,000 events and 20,000 unique drawables; UI exposes at most 500 results per query and loads thumbnails lazily |
| Shortcut label | Projection caps short label at 40 characters |
| Launch identity | Stable shortcut prefix plus validated capsule ID; own-window tasks use a stable `candy-capsule://<opaque-id>` document URI |
| Localization | Prototype own-window labels available in English and German; other locales use English fallback |
| Own-window option | `opensInOwnWindow`, default `false` for new and existing records; editable during creation and editing |
| Task sessions | Each Capsule task stores its own tabs, selection, stacks and task-removal marker; browser settings/profile definitions stay shared |

Legacy records may have only a rendered favicon icon. The editor preserves that bitmap unchanged while icon customization stays unchanged, never stores it as a source favicon, and switches explicitly to the Capsule emoji fallback when emoji or tile color changes. This prevents recursively embedding an already rendered tile.

Favicon and custom sources use different files. Choosing or replacing a custom crop never overwrites the website favicon; switching away from a custom crop retains it for later reuse. A persisted `custom` mode without a valid custom file resolves to the profile/emoji fallback instead of publishing a broken shortcut.

Icon packs are import sources, not a persisted icon mode. Choosing a pack drawable produces the same bounded custom bitmap as image import. The Capsule stores its own copy, so later pack updates or removal do not change an existing Capsule. Component mappings in `appfilter.xml` are searchable hints only; websites are never auto-mapped to Android app components. Package discovery uses targeted `<queries>` intents and does not request broad installed-app visibility.

## Own-window lifecycle

| Event | Behavior |
| --- | --- |
| Launch/relaunch | Android document mode reuses the existing task for the Capsule URI; different Capsules have separate Recents cards |
| Task identity | Recents uses Capsule name and rendered icon; the Capsule remains part of Candy rather than an installed app |
| Profile selection | Own windows require an existing local, unprotected profile; protected profiles open in main Candy with existing authentication. Private tabs are never used |
| Profile changes | Shared-preference and Capsule-file observers hide and close the task when its owner is deleted/protected/reassigned or own-window mode is disabled, including split screen; discarded task snapshots and engine files are cleared and cannot be recreated by destruction |
| Open full Candy / outside link | Hand off the validated target and Capsule ID to the main browser, select its owning profile, retain the Capsule task and its session |
| Persistence | Independent task tab snapshots cannot overwrite the main browser tabs or remembered profile selection |
| Session cleanup | Gecko, WebView, previews, favicons and trails retain IDs from all persisted Capsule tasks |
| Background snoozes | Main browser owns snooze scheduling and restoration; Capsule windows do not replace its scheduled alarms |
| Process integrations | Main browser owns private-tab notifications, sync observation and extension chrome/Topping interaction delegates; Capsule task lifecycle does not replace them |
| Candy Rules | Main browser owns persistent filter edits/hit counters; Capsule windows reload its rules on resume and atomic-file changes without writing stale snapshots |
| Media ownership | A task without the active publication cannot clear another task’s media session when it opens, navigates or closes |

## Main files

| Concern | File |
| --- | --- |
| Records | [`SiteCapsuleStore.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/data/SiteCapsuleStore.kt) |
| Icons | [`SiteCapsuleIconStore.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/data/SiteCapsuleIconStore.kt) |
| Custom import/crop | [`CapsuleCustomIconProcessor.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/capsule/CapsuleCustomIconProcessor.kt), [`CapsuleIconCropRules.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/capsule/CapsuleIconCropRules.kt) |
| Icon-pack import | [`CapsuleIconPackRepository.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/capsule/CapsuleIconPackRepository.kt), [`CapsuleIconPackRules`](../../app/src/main/java/dev/sk2andy/materialbrowser/capsule/CapsuleIconPack.kt) |
| Own-window launch | [`CapsuleWindowLauncher.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/capsule/CapsuleWindowLauncher.kt), [`SiteCapsuleActivity.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/SiteCapsuleActivity.kt) |
| Shortcuts | [`CapsuleShortcutPublisher.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/capsule/CapsuleShortcutPublisher.kt) |
| Deletion | [`CapsuleDeletionRules.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/capsule/CapsuleDeletionRules.kt) |
