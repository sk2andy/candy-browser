# Feed contracts

| Boundary | Contract |
| --- | --- |
| Ownership | One repository and atomic metadata file per normal profile; opaque hash paths under `files/feeds/`, immutable document generations in a profile directory |
| Private browsing | Fresh memory-only preview; subscriptions, reading state, tags, downloads, queue and rules cannot mutate persistent storage |
| Locks | Access checked before/after network and before writes; browser lock immediately invalidates observable cache; denied access clears it |
| Deletion | Profile deletion invalidates cache, cancels jobs/notifications and schedules serialized file cleanup |
| Network | HTTP(S) only, no browser cookies/authentication, checked redirects, finite timeouts and a 2 MiB response bound |
| XML | RSS 2, RSS/RDF and Atom; DTD/entity declarations rejected, external entities disabled; bounded node count/depth/item count |
| Content | Feed HTML becomes inert text. Reader mode optionally fetches inert article/main HTML and applies Reader sanitization; feed text remains fallback |
| Discovery | Fetch current HTTP(S) page, collect RSS/Atom alternate links, choose a candidate then preview before subscribing; manual URL remains available |
| Identity | Existing normalized HTTP URL rules, SHA-256 identity; duplicate article URLs retain all subscribed source IDs |
| Similar headlines | Bounded presentation grouping only; each article URL and state remains independent and can be expanded |
| Refresh | Existing content remains usable; source error and last-success state persist; opening refresh enabled by default, background work opt-in |
| Read state | Unread/Today/Starred/All/Hidden plus source/folder/tag/search filters; bulk read action affects displayed unread IDs and supports undo |
| Rules | Source/folder scope plus keyword conditions; mark read, star, hide, tag or notify; preview uses same matcher. Mutations apply to new items; refresh preserves manual choices |
| Search views | Persist name, query, source/folder, unread and tag criteria; evaluate current items when opened |
| Revisions | Changed feed text/title records update time and one previous text; refresh preserves read state; comparison shown only with a previous text |
| OPML | Preview new/existing/invalid entries; imports are idempotent and retain folder names; export includes subscriptions/folders, no article text or reading state |
| Limits | 500 subscriptions; 5,000 unprotected retained items, website/starred/offline items protected; 32 MiB metadata cap, bounded text, folders, searches, rules and queue |

All repository mutations return failures instead of announcing an uncommitted state. Corrupt stores fail visibly and are not overwritten by an empty successful load. Android backup allowlisting continues to exclude feed files from cloud backup; full app archives and device transfer follow the existing file/profile transfer boundary.
