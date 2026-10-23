# Navigation, library and reading

| Surface | Behavior |
| --- | --- |
| Browser menu | Page actions first; Feeds and Library below; custom workflows last and collapsed. Existing fixed shortcuts and footer remain |
| Unread chip | Counts visible unread feed articles, hides at zero, updates from repository state; website library entries do not contribute |
| Feeds | Inbox, Library, Sources and Tools; navigation rail at the existing large-screen threshold; native predictive back returns to browser |
| Direct library | Same library content without feed navigation; no subscription required |
| Search and filters | Existing Candy M3 search bar. Inbox status and tag chips share one horizontally scrollable row; All resets status, source, folder and tag. Library uses All / Websites / Feeds tabs, with a separate horizontally scrollable status-chip row; tab and filter selections remain independent |
| Batch download | Download saved articles appears only while starred articles still need an offline copy; already-ready and in-progress copies do not leave a disabled action on screen |
| Article cards | Separate reading target and action row. Bookmark and listening-queue toggles act directly without opening the article; outlined/filled bookmark represents marker state, independent of browser favorites. Tonal offline/update badges replace plain status text; additional actions remain in the source-row menu |
| Listening entry | Queue count in the collection header, full-width library entry and Reader actions all open the same player; opening it suspends the Reader surface and its own speech controller until dismissal; closing the player keeps an explicitly started listening session running |
| Article | Existing flat Reader surface, URL pill, typography/theme/speech controls; no feed header. Reader actions include next unread and shared library |
| List return | Reader overlays the collection; list filters, search and scroll remain composed. Opening the original navigates the selected browser tab; native back restores the collection with its saved filters and scroll |
| Profile/tab changes | Collection identity follows profile, tab and private mode; old UI scope is disposed and UI tasks cancelled. Normal-profile playback keeps its process-owned session; explicitly opening another profile’s player selects that profile and stops the previous session |

## Existing Reader articles

Profile-owned Reader snapshots are copied into the owning profile's collection on first library entry. Tags, marker, read state and queue then use the same article model as feed items. Originals are retained for Reader/document-provider compatibility; an explicit removal of a website offline copy also removes the corresponding earlier Reader record. Reimport of the same snapshot never restores a deliberately removed download or resets metadata. An explicitly saved newer snapshot updates the document and keeps article metadata. Original rows remain visible until that exact snapshot was imported successfully, including when the budget is full.

Snapshots without profile provenance are a separately disclosed, initially collapsed earlier collection. They are never silently assigned to the current profile. Private snapshots remain excluded. Existing Reader saving still follows its documented 20-snapshot legacy limit; profile collection copies already imported remain protected from that limit. Feed/collection downloads use a separate byte budget rather than entering the legacy snapshot list.

## Offline and audio

| Concern | Contract |
| --- | --- |
| Marker vs offline | Independent. Removing a marker keeps a downloaded copy; removing a download keeps metadata/marker |
| Budget | 250 MiB default, configurable 10–250 MiB; persistence rejects a full budget without silently deleting saved articles |
| Files | Documents are bounded to 4 MiB each and loaded individually; a metadata commit references already written immutable generations. Failed commits retain previous documents; orphan cleanup follows successful reads/writes |
| Migration | Earlier inline documents migrate one article at a time, with bounded streaming metadata and temporary disk space for atomic publication |
| States | None, Waiting, Downloading, Ready, Failed; interrupted Downloading restores as Waiting; retry and explicit removal available |
| Wi-Fi | Enabled by default; manual downloads wait without Wi-Fi; constrained WorkManager job retries pending work |
| Cleanup | Explicit per-article removal; saved list can be downloaded in a batch; original Reader copy cleanup only after explicit website removal |
| Reading mode | Per source: feed text, fetched Reader text or original page; original always available |
| Speech | Full-height Android TTS bottom sheet with the same visual drag indicator as the browser menu and no header close button, initially paused, with source favicon (monogram fallback) and native M3 wavy progress. Actual text progress in percent, play/pause, next, speed and explicit stop. Visible queue can move items up/remove them; adding bookmarks appends missing items without replacing the list. A profile-owned MediaSessionService keeps explicitly started speech playing after player dismissal or app backgrounding and provides Android media/notification controls. Speech has no fabricated duration or system seek position |
| Queue state | Direct per-article membership changes are serialized against current repository state. Skipping/removing preserves partial progress and does not mark an article read; successful speech completion alone marks it complete. Completion commits read state, progress and queue removal atomically in the process-owned playback scope, serialized with queue edits. Opening and closing without playback preserves earlier progress |
| Playback access | Protected-profile lock, profile deletion, guest mode and app-data transfer stop speech and clear system metadata. A normal profile session can continue across tab/profile navigation; private articles never enter the service. Transient audio-focus loss pauses speech; explicit play resumes |
| Missing voice | Visible unavailable state; no false playback success |

## Background refresh and notifications

Background refresh, selected-source/rule notifications and digest are opt-in. WorkManager checks roughly hourly with connectivity constraints; Android may defer execution. Digest target hour and quiet hours are profile settings. An unread cursor and local-day marker avoid repeated delivery; missed targets can be delivered on a later permitted run. Permission denial preserves selection and explains Android's disabled state.

Refresh notifications contain counts only, have secret lock-screen visibility and never retain article titles. Locked/unavailable profiles cannot refresh or publish. Tapping a notification opens the owning accessible profile's feeds. The separate playback notification exposes only the explicitly playing normal-profile article and opens its owning accessible profile directly in the listening player. Media3 owns its media-style notification and foreground lifecycle. No exact alarms, RSS account or remote synchronization is introduced.
