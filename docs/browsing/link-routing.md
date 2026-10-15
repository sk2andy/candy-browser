# Personal incoming-link rules

| User action | Behavior |
| --- | --- |
| Settings → Browser → Personal link rules | Add, edit, enable, disable, reorder or delete domain rules |
| Exact domain | Matches the canonical domain only; `github.com` does not match `api.github.com` |
| Include subdomains | Also matches whole-label descendants; lookalike suffixes never match |
| Profile target | Opens a new regular tab in the explicitly selected local profile |
| Reader Studio target | Loads the URL in a regular tab of the active eligible profile, then uses the existing DOM extraction and Reader Studio UI |
| Site Capsule target | Opens the accepted URL inside the selected capsule, including its separate window when configured |
| Multiple matching rules | First enabled matching rule in the displayed order wins |

## Boundaries

| Concern | Contract |
| --- | --- |
| Entry point | Only HTTP(S) `ACTION_VIEW` requests Android passes to Candy; internal navigation, shared text and links returned by an app Candy just opened retain existing handling |
| URL validation | `BrowserUriPolicy.normalizeHttpUrl`; bounded URL and canonical host matching |
| Opt-in | No rules exist by default; domains and destinations are explicitly configured |
| Private browsing | Incoming links while the selected tab is private bypass automatic routing |
| Targets | Missing, protected and synced profiles cannot be automatic destinations; disabling profiles disallows switching to another profile |
| Stale targets | A matching rule whose destination disappeared or became unavailable reports the issue and stops; it does not fall through into another profile |
| Capsule boundary | Target URL must satisfy the existing capsule navigation policy |
| Reader identity | Deferred Reader opening is memory-only and tied to the selected regular tab and exact accepted URL; errors, tab changes and redirects cancel it |
| Persistence | At most 64 normalized rules in atomic `files/link_routing_v1.json`; writes serialized off main; unknown target kinds or file versions fail closed |
| Privacy | Stored configuration contains user-entered domains and opaque destination IDs; visited URLs, selected text and private tab state never enter this store |
| Platform | Android settings entry; shared destination enum supports routing without exposing an unsupported iOS entry |

## Ownership and verification

| Concern | Source / test |
| --- | --- |
| Matching, priorities, target validation, deferred Reader identity | `LinkRoutingRules`, `LinkRoutingRulesTest` |
| Atomic configuration and malformed data | `LinkRoutingStore`, `LinkRoutingStoreInstrumentedTest` |
| Runtime routing and queued Reader state | `IncomingLinkRouter`, `MainActivity`, focused `BrowserScreen` effect |
| Settings editor | `LinkRoutingSettingsPage`, `LinkRoutingSettingsPageInstrumentedTest` |
