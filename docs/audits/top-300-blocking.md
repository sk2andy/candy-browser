# Top-300 cookie and advertising audit

## Scope

This audit measures Candy Browser's default cookie-banner and advertising protection in a clean
Android emulator. It uses the fixed top 300 ranks from Tranco list `PYG5J`, generated on
2026-08-06. Tranco aggregates multiple popularity rankings over 30 days; the pinned list makes the
sample reproducible.

Tranco ranks domains rather than guaranteed web pages. Infrastructure domains, DNS failures,
non-HTML responses, bot challenges, and redirects remain in the fixed cohort and are reported
instead of silently replaced. A second coverage count identifies usable HTML pages; it is not
described as a separate "top 300" ranking.

## Environment

| Field | Value |
|---|---|
| Ranking | Tranco `PYG5J`, ranks 1–300 |
| Ranking source | `https://tranco-list.eu/download/PYG5J/1000000` |
| Ranking generated | 2026-08-06T22:25:03Z |
| Ranking ZIP SHA-256 | `a115b470bc6487414f36b5c6bb16f1b22dad8fc025be0b36352bfbab6f2a6023` |
| Baseline source revision | `9033f1e053f3312bb5bf3542437ddee07320fc5a` |
| Emulator | `Medium_Phone_API_36.1`, arm64, 1080×2400 |
| Android | API 36.1 |
| WebView | 150.0.7871.181 |
| App locale | `de-DE` |
| Time zone | `Europe/Berlin` |
| Browser | Candy Browser `MainActivity` and production `BrowserController` |
| Authentication | None |
| Interaction | No ad clicks, CAPTCHA bypass, or login; candidate may invoke audited Reject/Later consent actions |
| Analysis build SHA-256 | `c4d0a1dc007c1424ac6e454681f9fe1d987a096fe14295ee3bad5a2f0bdeb83d` |
| Hardened candidate SHA-256 | `89beb53e689eb6d93c412ecd44290993837d7b40f1e513c64d8ae4f33adea0d7` |

## Passes

| Pass | Ads/trackers | Cookie hiding | Third-party cookies | Purpose |
|---|---:|---:|---:|---|
| Baseline | Off | Off | Allowed | Observe unprotected page behavior |
| Current | On | On | Blocked | Measure shipped defaults |
| Candidate | On | On | Blocked | Verify proposed Candy defaults |

`Current` is measured from baseline source revision `9033f1e`; `Candidate` is measured after the
new bundled rules are applied. The two pass names therefore identify both settings and source
revision. Rebuilding `current` from the candidate branch would not recreate the baseline binary.

Each target starts in a newly created isolated incognito WebView profile. Candy opens the HTTPS root, waits up
to 12 seconds for navigation, then allows two seconds for dynamic DOM changes. A timeout does not
discard a rendered DOM. The probe records final URL without query or fragment, content type, visible
text length, challenge state, scroll lock, known and heuristic CMP elements, high-confidence ad
elements, third-party performance-resource hosts, and Candy's blocked-domain summary. A
document-start probe runs in every WebView frame; cross-origin subframes report only bounded CMP/ad
counts and their host to the top-frame probe. Screenshots are taken only when a visible cookie/ad
candidate or challenge remains.

The DOM probe deliberately does not classify an element as advertising solely because an arbitrary
class contains `ad`. Network rules require an exact host plus either evidence across independent
first parties or a clear single-purpose vendor. Multifunctional hosts use site-scoped pairs. Cosmetic
rules remain origin-scoped. Broad host lists retain the same-party escape. The separate bundled
path-rule format may override that escape only for an audited exact page-host, request-host, and
literal non-root path prefix; main-frame navigations are never evaluated. The shipped path asset is
currently empty.
Generic `promo` tokens are not classified as advertising: they commonly identify editorial polls,
product cards, and first-party navigation. This was verified by removing the token and rerunning all
300 ranks; NYTimes then reported zero visible ad candidates while its real DFP slots stayed hidden.

## Reproduction

Start the named AVD, then run 25-site batches against its explicit serial. The runner clears app
data before each batch and immediately pulls the result because the System WebView provider can
stall after a large number of named profile deletions in one process:

```bash
for start in $(seq 1 25 276); do
  scripts/run_top_site_audit.sh emulator-5560 candidate "$start" 25
done
```

The runner refuses non-emulator devices. A range may be split into smaller batches if the installed
System WebView provider stalls while deleting many named profiles. Results are written under
`build/top-site-audit/<pass>/` as JSON Lines plus scoped screenshots. The test is opt-in and
skipped by normal connected-test runs. Use `scripts/summarize_top_site_audit.mjs` to validate the
rank-to-domain fixture mapping, one non-empty pass/build ID, and exactly the contiguous ranks 1–300.

## Results

Results and accepted rules are filled from the completed runs. Cosmetic cookie rules only remove
visual UI. Separately listed consent actions may invoke a known Reject or Remind-later control; they
never invoke Accept. Neither mechanism guarantees that first-party storage is absent.

The fixed cohort contains infrastructure domains and error pages. A usable page is an HTML document
with at least 200 visible-text characters, no challenge, and no main-frame error. The hardened run
produced 154 usable pages; 116 targets had a main-frame error. Those targets remain in the
denominator and are not replaced.

| All fixed ranks 1–300 | Discovery analysis | Hardened candidate |
|---|---:|---:|
| Completed records | 300 | 300 |
| Usable HTML pages | 158 | 154 |
| Visible cookie-UI sites | 36 | 24 |
| Visible high-confidence ad sites | 25 | 9 |
| Scroll-locked sites | 9 | 11 |
| Main-frame errors | 113 | 116 |
| Timeouts | 5 | 10 |
| Requests blocked with HTTP 204 | 296 | 297 |
| Sites with at least one blocked request | 89 | 90 |
| Sites with observed cross-origin frames | not measured | 45 |
| Cross-origin frames with visible cookie/ad UI | not measured | 0 |

The discovery run used the original top-frame probe; the hardened verifier added cross-origin frame
reports and removed the generic `promo` ad heuristic. The two columns therefore show audit stages,
not a controlled causal benchmark. Errors, timeouts, and request totals are also live-network
observations. Per-rank records remain available so variance is not hidden behind aggregate counts.
The higher scroll-lock count is conservative by design: the final implementation no longer
overrides class/computed `overflow` values and unlocks only an inline lock paired with a known hidden
CMP.

The final per-rank result is [top-300-candidate-final.csv](results/top-300-candidate-final.csv)
(SHA-256 `849da032f9b3db3b9767074daa6ff36b0f320adaed6796b664f550bdfafb25c1`).

### Accepted default rules

| Type | Sites | Basis |
|---|---|---|
| Reject/Later action | `google.com`, `web.de`, `gmx.net`, `www.nytimes.com` | Exact frame host plus exact ID; never Accept |
| Cookie cosmetic | Bing, X, Roblox, Yahoo, MSN, Opera, WordPress, Gravatar, EU, Shopify, Forbes, Ubuntu, Roku, TinyURL, ChatGPT, NYTimes | Site-scoped stable ID, semantic attribute, or product-specific class |
| Ad cosmetic | web.de, GMX, NYTimes, Mail.ru, Dzen/Yandex, Reddit, Flickr, Guardian, Nature | Site-scoped ad API/slot marker or explicit product-specific ad class |
| Request path asset | None yet | Parser and precedence are shipped, but no first-party path was safe enough to add from one run |

Rejected examples include generic `promo`, `sticky`, and `mobile-nav` classes; Pinterest, Canva,
Twitch, and several other pages exposed only obfuscated selectors. These remain visible rather than
risking content or login breakage.

### Targeted blocker smoke test

Candidate APK `06ab94968fbbd75f1a4b9ff9ea51ab753298628cf19a6375c99e89ae921cb022`
was installed into separately cleared emulator profiles on 2026-08-07. Targeted diagnosis used the
WebView debugging protocol for DOM and network metadata only; final verification used cold-profile
screenshots and accessibility dumps. Debugging is disabled in the shipped source, and no page
content was modified by either probe.

| Site | Fresh-profile result | Network evidence |
|---|---|---|
| `web.de` | Final URL returned to `https://web.de/`; no consent control, registration promotion, audited ad slot, or empty top-ad wrapper visible | The clean consent flow used the exact cross-origin `plus.web.de` `#reminder` control |
| `gmx.net` | Final URL returned to `https://www.gmx.net/`; no consent control, registration promotion, Lotto promotion, service-slot ad, or empty top-ad wrapper visible | The consent frame was observed at `plus.gmx.net`; its exact `#reminder` control was used |
| `nytimes.com` | No Fides element or audited ad slot visible; document remained scrollable (`22,318 > 890` px) | 5 HTTP 204 responses, covering Google Publisher Tag, Amazon Ads, Media.net, and GTM |

The UIM distinction matters: `dl.web.de` and `dl.gmx.net` connector frames can remain on a normal
homepage. Consent actions are therefore scoped only to the exact `plus.web.de` or `plus.gmx.net`
frame host and exact `#reminder` selector.
