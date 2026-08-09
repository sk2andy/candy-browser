# Multilingual advertising-blocking audit

## Scope

This follow-up checks live advertising in Candy and Arc Search across commercial search, social,
news, shopping, portal, and weather pages. It supplements deterministic fixtures; it does not claim
that a finite sample proves every page or future inventory ad-free.

Twenty-seven distinct scenarios were inspected in German, English, French, Spanish, Italian,
Polish, Dutch, Portuguese, Japanese, and Korean. Usable runs rendered organic content and were
checked at the initial viewport plus three scroll checkpoints. Every positive text/accessibility
candidate was classified with a screenshot or DOM inspection. Consent-only, challenge, and stale
navigation runs were discarded.

## Comparator and environment

| Field | Value |
|---|---|
| Candy | Debug APK SHA-256 `8738178881af263cfbb6090d65e91237469d9156145f31e8d14c71487b4487fe` |
| Arc | Play-installed Arc Search 1.12.9 (94), base APK SHA-256 `05ce7a8ad29863ba0195dea68c2afc09bfc48d974c9a280915e58cf156d7b661` |
| Emulator | `Medium_Phone_API_36.1`, Android 16 / API 36.1, 1080×2400 |
| WebView | 134.0.6998.135 |
| Locale / time zone | German / Europe-Berlin |
| Checkpoints | initial view plus three downward scrolls, two-second settle per scroll |
| Positive labels | `Sponsored`, `Promoted`, `Advertisement`, `Anzeige`, `Publicité`, `Publicidad`, `Pubblicità`, `Reklama`, `Publicidade`, `広告`, `광고`, and local variants |

## Paired results

| Language / scenario | Arc Search | Candy final | Classification |
|---|---|---|---|
| DE Google `hotel berlin` | sponsored hotel/result units | no labelled ad unit; organic results present | Candy better |
| DE Google `kredit vergleich` | multiple `Anzeigen` results | no labelled ad unit; organic results present | Candy better |
| EN Reddit `/r/popular/` | complete promoted post | no promoted post over four checkpoints | Candy better |
| DE Amazon laptop search | many sponsored products | no sponsored product over four checkpoints after `amazon.*` compiler fix | Candy better |
| PL Interia | `REKLAMA` slots and complete `ARTYKUŁ SPONSOROWANY` card | sponsored card and large slots removed; footer advertising link remains | Candy better |
| KO Coupang | product advertising plus advertising-info link | personalized ad carousel removed; category-ad module, footer advertising-info link, and first-party app promo remain | Partial improvement; residual paid module |
| PT UOL | many 75–464 px `Publicidade` containers | visible containers removed; organic news present; hidden/footer labels remain in accessibility | Candy better |
| KO Naver | platform ads plus the same `[광고]` user clip | platform `AD` frames removed; `[광고]` user clip remains | Candy at least Arc parity |
| DE BILD | visible `ANZEIGE` / partner-ad labels | no paid creative in screenshot; residual DOM label possible | Candy better visually |
| DE Focus | visible/sticky advertising | no paid creative in screenshot; residual DOM label possible | Candy better visually |
| DE wetter.com | visible `ANZEIGE` in Arc | no paid creative in Candy screenshot; organic forecast/news present | Candy better visually |
| FR Le Figaro | visible advertising | empty `Publicité` placeholder, no paid creative | Creative blocked; empty-slot gap |
| FR Le Monde | visible advertising | label/empty-slot candidates, no verified paid creative | Creative blocked; empty-slot gap |
| FR 20 Minutes | visible advertising | empty branded `Publicité` slot, no paid creative | Creative blocked; empty-slot gap |
| ES El Mundo | visible `Publicidad` | label remains, screenshot contains organic content only | Creative blocked; empty-slot gap |
| ES El País | visible advertising | no paid creative in inspected screenshot | Candy better visually |
| IT Repubblica | visible `Pubblicità` | no paid creative in inspected screenshot | Candy better visually |
| IT Gazzetta | visible advertising in Arc | Candy candidates were footer/policy text; organic content rendered | Candy better in usable viewport |
| NL Telegraaf | sponsored candidate in Arc | organic page screenshot; no paid creative in inspected viewport | Candy better visually |

Candy-only smoke checks on t-online, Yahoo Japan, and ITmedia rendered organic content without a
verified paid creative. The ITmedia keyword candidate was a news headline about fraudulent
advertising, not an ad.

## Invalid or inconclusive runs

| Scenario | Reason | Claim |
|---|---|---|
| Golem | advertising/tracking consent wall, little organic content | no ad-free claim |
| Cdiscount | anti-bot/challenge state | no ad-free claim |
| Corriere | consent/subscription wall covered the page; background accessibility text was not visually scoreable | no ad-free claim |
| WP | consent overlay dominated the inspected run | no ad-free claim |
| Marca | consent text dominated the positive candidates | no ad-free claim |

## Bugs found and fixed

| Bug | Evidence | Fix |
|---|---|---|
| Entity wildcard compiler rejected valid single-label patterns | `amazon.*` was absent from generated asset; Amazon showed 14 sponsored candidates | accept one fixed label; ICANN suffix matching still rejects lookalikes such as `amazon.evil.com` |
| Core uAssets cosmetics were excluded | BILD/El Mundo/T-Online rules existed upstream but never reached Candy | compile domain-specific standard CSS and exceptions into separate GPL asset; merge with EasyList before exception resolution |
| Interia first-party advertising | `.common-ad` slots and `.news-li` sponsored cards stayed visible | two origin-scoped standard-CSS rules with a same-structure organic `.news-li` guard |
| Corriere ad placeholders | `.card--adv` remained | origin-scoped rule; live sponsored-content result remains inconclusive behind consent wall |
| Naver platform frames | multiple large `iframe[title="AD"]` units | origin-scoped frame selector; deliberately does not hide organic clip section by Korean text |
| Coupang paid home carousel | `.personalized_ads` | origin-scoped rule with same-structure organic carousel guard; broad `#categoryBestUnit` rule rejected during review |
| UOL ad slots | repeated `.cardAd` containers up to 464 px high | origin-scoped rule with organic-news guard fixture |

## What “uBlock list” means here

Candy now consumes the uBlock Origin core `filters.txt` source for both its supported network subset
and domain-specific standard cosmetics. It does not yet implement full uBO semantics: generic
cosmetics, URL/path and resource-type matching, procedural selectors, scriptlets, redirects, and
HTML filters remain unsupported. Regional stock lists are separate, optional assets in uBO and are
not bundled wholesale; selected live gaps were fixed with independently verified, origin-scoped
selectors to avoid importing incompatible licenses or high-breakage syntax.

The core uAssets cosmetic compiler produced 2,002 hides and 50 exceptions. EasyList/EasyPrivacy
produced 16,497 hides and 652 exceptions. After cross-source deduplication and exception handling,
Candy loads 19,189 unique domain-specific records (18,487 hides and 702 exceptions).

## Remaining gaps

| Gap | Impact | Next safe step |
|---|---|---|
| Naver `[광고]` user clip shares DOM structure with organic clips | same labelled clip remains in Candy and Arc | add procedural text filtering only with a general engine and organic-loss tests; do not hide whole clip feed |
| Empty branded slots on some French/Spanish publishers | no paid creative, but blank space/label remains | add selectors only after stable container DOM is captured |
| Top-frame-only cosmetic injection | cross-origin iframe internals cannot be cosmetically filtered | rely on network interception or add frame-aware support with explicit same/cross-origin tests |
| No regional stock-list bundle | local first-party ads can outpace core lists | evaluate per-locale opt-in assets with license, update, exception, and breakage gates |
| Finite live inventory | ads vary by geo, time, consent, and auction | retain deterministic fixtures and repeat paired live runs before releases |
