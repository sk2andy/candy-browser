package dev.sk2andy.materialbrowser.shared

import dev.sk2andy.materialbrowser.shared.browser.AddressResolution
import dev.sk2andy.materialbrowser.shared.browser.BrowserUrlRules
import dev.sk2andy.materialbrowser.shared.topping.ToppingInjectionPlan
import dev.sk2andy.materialbrowser.shared.topping.ToppingParseResult
import dev.sk2andy.materialbrowser.shared.topping.ToppingRules

class CandySharedFacade {
    fun resolveAddress(input: String): AddressResolution = BrowserUrlRules.resolve(input)

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
