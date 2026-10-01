package dev.sk2andy.materialbrowser.browser.gecko

import android.content.res.Resources
import dev.sk2andy.materialbrowser.R

internal object GeckoHttpsOnlyErrorPageResources {
    fun strings(resources: Resources): GeckoHttpsOnlyErrorPageStrings = GeckoHttpsOnlyErrorPageStrings(
        language = resources.configuration.locales[0].toLanguageTag(),
        title = resources.getString(R.string.https_only_warning_title),
        protection = resources.getString(R.string.https_only_warning_protection),
        explanation = resources.getString(R.string.https_only_warning_explanation),
        risk = resources.getString(R.string.https_only_warning_risk),
        back = resources.getString(R.string.https_only_warning_back),
        retry = resources.getString(R.string.https_only_warning_retry),
        options = resources.getString(R.string.https_only_warning_options),
        exception = resources.getString(R.string.https_only_warning_exception),
        openHttp = resources.getString(R.string.https_only_warning_open_http),
        lifetime = resources.getString(R.string.https_only_warning_lifetime),
        unavailable = resources.getString(R.string.https_only_warning_unavailable),
    )
}
