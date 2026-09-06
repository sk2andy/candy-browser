# Obtainium listing

GitHub Releases already expose stable, signed APK assets. Candy needs explicit filters so each
separately installable package stays on its selected release channel.

| Channel | APK filter |
| --- | --- |
| Standard | `^CandyBrowser-v[0-9]+(?:\.[0-9]+){1,2}-release\.apk$` |
| User CA | `^CandyBrowser-v[0-9]+(?:\.[0-9]+){1,2}-ca-release\.apk$` |

`dev.sk2andy.materialbrowser.json` is ready to copy into the Obtainium community-listing repository
at `public/data/apps/complex/dev.sk2andy.materialbrowser.json`. Open a pull request against
`ImranR98/apps.obtainium.imranr.dev`; the two entries use separate package IDs and can be installed
in parallel.

The README's main Obtainium badge uses only the standard filter. Keep the User CA link secondary
and beside its security warning. If prerelease APKs are introduced later, add a separately labeled
configuration instead of weakening the stable filters.
