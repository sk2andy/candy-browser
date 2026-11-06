# eMudhra certificate response and GeckoView 159

Candy's Android GeckoView pin previously used `157.0.20260924084938`. On 2026-11-06,
[Mozilla Maven metadata](https://maven.mozilla.org/maven2/org/mozilla/geckoview/geckoview/maven-metadata.xml)
listed `159.0.20261102144711` as the current release. Its AAR requires compile SDK 37.2.

[Mozilla's incident report](https://blog.mozilla.org/security/2026/10/20/protecting-firefox-users-following-the-compromise-of-emudhra-intermediate-certificates/)
describes OneCRL distribution and the Android bundled-list startup fix. Inspecting the two
published GeckoView AARs' `assets/omni.ja` found:

| Evidence | 157 | 159 |
| --- | --- | --- |
| Bundled `defaults/settings/security-state/onecrl.json` | No matching entries for the published compromised intermediates | Active subject/public-key-hash entries match all six TLS intermediates and their six cross-signs; issuer/serial entries also match the six eMudhra-issued certificates |
| GeckoView startup | Calls `RemoteSecuritySettings.init()` | Calls `RemoteSecuritySettings.init()`, which loads the bundled OneCRL records into certificate storage |

The matches were compared to the [incident's intermediate certificate list](https://emudhra.we-have-a-problem.com/impacted-intermediates.json).
Two code-signing intermediates and their cross-signs did not match that bundled TLS blocklist;
this comparison does not establish code-signing trust behavior. The 157 bundle's absence of entries
does not prove a running installation lacks remotely updated revocation data. Candy does not override
OneCRL or CRLite preferences in its runtime factory. Effective remote updates and startup timing
depend on the running engine and profile; bundled contents alone do not prove a live rejection.

Candy also offers Android System WebView and uses Android networking for features such as feeds,
updates and sync. Their trust and revocation state is independent of Gecko. Google reports an
[Android Play services blocklist update](https://blog.google/security/protecting-chrome-and-android-users-from-compromised-emudhra-intermediate-ca-certificates/),
but that is not a guarantee for every Android device or WebView provider.

## Emulator check

On a fresh API 36 Google Play emulator, separate diagnostic FullDebug builds based on commit
`4d8a227` using Gecko `157.0.20260924084938` and `159.0.20261102144711` each loaded
`https://example.com/` and rejected both `dv-g3b-emudhra.we-have-a-problem.com` and
`dv-g3b-aaa-emudhra.we-have-a-problem.com`. The Gecko 159 check was repeated with the same results
on the current base `c055503`.
Temporary logging of Gecko's `onLoadError` returned category `ERROR_CATEGORY_SECURITY` and code
`ERROR_SECURITY_BAD_CERT` for both hosts in both builds. The log was removed after testing. This
identifies a certificate failure, but does not identify its precise cause or prove a particular
revocation mechanism was responsible. The old and new builds each used a freshly cleared test
app profile; the result does not generalize to all profiles, devices or configurations. The
Gecko runtime settings factory, load-error callback and error mapping are unchanged between that
commit and the current base `c055503`.

On `c055503` with the upgrade applied, `lintFullDebug`, `lintFossDebug`, `assembleFullDebug`,
`assembleFossDebug`, `verifySystemWebViewReleaseDependencies` and `verifyFossReleaseDependencies`
passed. The dedicated emulator passed all five `GeckoRuntimeSettingsInstrumentedTest` cases.
System WebView and Android non-browser TLS were inspected but not exercised against these hosts.

To inspect an AAR, extract `assets/omni.ja`, then inspect
`defaults/settings/security-state/onecrl.json`, `chrome/geckoview/content/geckoview.js` and
`modules/psm/RemoteSecuritySettings.sys.mjs` within it. For a live check, open a healthy HTTPS
control and the [incident's test hosts](https://emudhra.we-have-a-problem.com/) on a dedicated
emulator and capture the engine's actual certificate error. A generic load failure or the site's
checkmark alone is not evidence of certificate rejection. Never bypass the warning.
For a temporary diagnostic build, log the `code` and `category` from the `WebRequestError` passed
to `GeckoViewRuntimeHandle`'s `onLoadError`; remove the log before shipping. Candy currently maps
these certificate failures to its generic “Website not found” screen, so that screen does not
establish the underlying reason on its own.
