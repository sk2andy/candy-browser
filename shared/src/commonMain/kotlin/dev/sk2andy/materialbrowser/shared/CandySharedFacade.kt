package dev.sk2andy.materialbrowser.shared

import dev.sk2andy.materialbrowser.browser.PageTranslationProvider
import dev.sk2andy.materialbrowser.browser.PageTranslationRules
import dev.sk2andy.materialbrowser.shared.browser.AddressResolution
import dev.sk2andy.materialbrowser.shared.browser.BrowserChromeGestureRules
import dev.sk2andy.materialbrowser.shared.browser.BrowserUrlRules
import dev.sk2andy.materialbrowser.shared.topping.ToppingInjectionPlan
import dev.sk2andy.materialbrowser.shared.topping.ToppingParseResult
import dev.sk2andy.materialbrowser.shared.topping.ToppingRules

class CandySharedFacade {
    fun resolveAddress(input: String): AddressResolution = BrowserUrlRules.resolve(input)

    fun defaultPageTranslationUrl(
        sourceUrl: String?,
        targetLanguage: String,
    ): String? = pageTranslationUrl(
        provider = PageTranslationProvider.fromStableId(null),
        sourceUrl = sourceUrl,
        targetLanguage = targetLanguage,
    )

    fun pageTranslationUrl(
        provider: PageTranslationProvider,
        sourceUrl: String?,
        targetLanguage: String,
    ): String? = PageTranslationRules.buildTranslationUrl(
        provider = provider,
        sourceUrl = sourceUrl,
        targetLanguage = targetLanguage,
    )

    fun addressBarOverviewProgress(dragY: Double): Double =
        BrowserChromeGestureRules.overviewProgress(dragY)

    fun shouldOpenTabOverview(
        dragX: Double,
        dragY: Double,
        touchSlop: Double,
        isAddressEditing: Boolean,
    ): Boolean = BrowserChromeGestureRules.shouldOpenOverview(
        dragX = dragX,
        dragY = dragY,
        touchSlop = touchSlop,
        isAddressEditing = isAddressEditing,
    )

    fun bundledDemoTopping(): ToppingInjectionPlan {
        val source = """
            // ==UserScript==
            // @name Candy iOS bridge marker
            // @match https://*/*
            // @run-at document-start
            // ==/UserScript==
            document.documentElement.dataset.candyToppingRuntime = "wkuserscript";
        """.trimIndent()
        val script = (ToppingRules.parse("candy-ios-marker", source) as ToppingParseResult.Accepted).script
        return ToppingRules.injectionPlan(script)
    }
}
