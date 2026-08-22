# Curated tracker-host audit

Candy's small owned host asset may contain a parent host only when a maintained source supports the
same parent-level decision and the WebView runtime provides a bounded compatibility guard. Synthetic
test membership by itself is not evidence.

| Host | Maintained evidence | Runtime boundary |
| --- | --- | --- |
| `adjust.com` | HaGeZi Ultimate at revision `4952c89a3ee5e87e173cef9c6c21a17345dfdc24` contains exact `||adjust.com^` | Main frames and first-party Adjust pages remain allowed |
| `kochava.com` | Same pinned HaGeZi Ultimate revision contains exact `||kochava.com^` | Main frames and first-party Kochava pages remain allowed |
| `xp.apple.com` | Same pinned HaGeZi Ultimate revision contains exact `||xp.apple.com^`; pinned EasyPrivacy separately identifies `/config/*/report/` and `/report/` telemetry paths | Main frames and first-party Apple telemetry host pages remain allowed |

Sources:

- HaGeZi Ultimate: `https://raw.githubusercontent.com/hagezi/dns-blocklists/4952c89a3ee5e87e173cef9c6c21a17345dfdc24/adblock/ultimate.txt`
- EasyPrivacy: `https://github.com/easylist/easylist/tree/54849f55642f155a67649b46fe3b87c39607c1c5/easyprivacy`

The August 2026 comparison audit deliberately did not promote `inmobi.com`: pinned uAssets contains
an explicit scoped exception for `cmp.inmobi.com`. Consent managers, social APIs, login endpoints,
YouTube/Googlevideo, and video/CDN hosts also remain outside the global asset. Those require exact
maintained path or page-scope semantics before Candy can adopt them safely.
