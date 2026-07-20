package dev.sk2andy.materialbrowser.blocking

data class BlockerSettings(
    val blockAdsAndTrackers: Boolean = true,
    val hideCookieConsent: Boolean = true,
    val blockThirdPartyCookies: Boolean = true,
)
