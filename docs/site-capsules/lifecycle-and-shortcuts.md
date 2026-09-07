# Lifecycle and shortcuts

## Lifecycle lookup

| Stage | Source | Result |
| --- | --- | --- |
| Edit | `SiteCapsuleEditorActivity`, `SiteCapsuleEditorContract` | Bounded request/submission crossing an Android activity contract; favicon, custom crop, icon emoji and accessible tile color restore into the live preview |
| Import/crop | `CapsuleCustomIconEditorActivity`, `CapsuleCustomIconProcessor`, `CapsuleIconCropRules` | Open `image/*` through the system document picker, reject invalid/oversized content, downsample off-main, then produce a bounded square crop with pan/pinch/zoom controls |
| Icon pack | `CapsuleIconPackPickerActivity`, `CapsuleIconPackRepository`, `CapsuleIconPackRules` | Discover compatible installed packs through targeted theme intents, read bounded `drawable.xml` or `appfilter.xml` catalogs from `res/xml`, `res/raw` or assets, search unique drawables, and render the selected resource off-main |
| Create/update | `BrowserController` + `SiteCapsuleRules` + `CapsuleIconUpdateRules` | Persist normalized Capsule-owned icon settings, resolve legacy source availability, and re-render the icon/shortcut |
| Launch | `CapsuleIntentRules` → `MainActivity` | Open known opaque capsule ID or fall back to normal home |
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
| Launch identity | Stable shortcut prefix plus validated capsule ID |

Legacy records may have only a rendered favicon icon. The editor preserves that bitmap unchanged while icon customization stays unchanged, never stores it as a source favicon, and switches explicitly to the Capsule emoji fallback when emoji or tile color changes. This prevents recursively embedding an already rendered tile.

Favicon and custom sources use different files. Choosing or replacing a custom crop never overwrites the website favicon; switching away from a custom crop retains it for later reuse. A persisted `custom` mode without a valid custom file resolves to the profile/emoji fallback instead of publishing a broken shortcut.

Icon packs are import sources, not a persisted icon mode. Choosing a pack drawable produces the same bounded custom bitmap as image import. The Capsule stores its own copy, so later pack updates or removal do not change an existing Capsule. Component mappings in `appfilter.xml` are searchable hints only; websites are never auto-mapped to Android app components. Package discovery uses targeted `<queries>` intents and does not request broad installed-app visibility.

## Main files

| Concern | File |
| --- | --- |
| Records | [`SiteCapsuleStore.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/data/SiteCapsuleStore.kt) |
| Icons | [`SiteCapsuleIconStore.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/data/SiteCapsuleIconStore.kt) |
| Custom import/crop | [`CapsuleCustomIconProcessor.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/capsule/CapsuleCustomIconProcessor.kt), [`CapsuleIconCropRules.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/capsule/CapsuleIconCropRules.kt) |
| Icon-pack import | [`CapsuleIconPackRepository.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/capsule/CapsuleIconPackRepository.kt), [`CapsuleIconPackRules`](../../app/src/main/java/dev/sk2andy/materialbrowser/capsule/CapsuleIconPack.kt) |
| Shortcuts | [`CapsuleShortcutPublisher.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/capsule/CapsuleShortcutPublisher.kt) |
| Deletion | [`CapsuleDeletionRules.kt`](../../app/src/main/java/dev/sk2andy/materialbrowser/capsule/CapsuleDeletionRules.kt) |
