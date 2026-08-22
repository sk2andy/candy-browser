package dev.sk2andy.materialbrowser.blocking

import android.content.Context
import androidx.annotation.VisibleForTesting
import java.io.ByteArrayOutputStream
import java.util.concurrent.CompletableFuture

class ContentBlocker(context: Context) {
    private data class CompiledCosmeticRules(
        val rules: EasyListCosmeticRules,
        val genericPayload: GenericCosmeticPayload,
    )

    private val appContext = context.applicationContext
    private val candyDefaultRules by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        BundledCandyRules.parseOrEmpty(readAssetOrEmpty("candy_default_rules.txt"))
    }
    private val compiledCosmeticRulesFuture by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        CompletableFuture.supplyAsync {
            val rules = EasyListCosmeticRules.merge(
                EasyListCosmeticRules.parse(
                    readAssetOrEmpty("easylist_cosmetic_rules.txt"),
                    EasyListCosmeticRules.EASYLIST_V2_HEADER,
                ),
                EasyListCosmeticRules.parse(
                    readAssetOrEmpty("uassets_cosmetic_rules.txt"),
                    EasyListCosmeticRules.UASSETS_HEADER,
                ),
            )
            CompiledCosmeticRules(
                rules = rules,
                genericPayload = GenericCosmeticPayload.create(rules.genericSelectors()),
            )
        }
    }
    private val bundledBlockingSnapshot = BundledBlockingSnapshotProvider.get(appContext)
    private val consentCss by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        loadConsentCss(appContext)
    }
    private val consentScriptFuture by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        CompletableFuture.supplyAsync {
            ConsentBlockerScript.create(
                cssBytes = consentCss,
                siteRules = candyDefaultRules.cookieCosmeticRules,
            )
        }
    }
    val consentScript: String
        get() = consentScriptFuture.join()
    val consentRemovalScript: String = ConsentBlockerScript.removalScript

    fun prepareConsentScript() {
        consentScriptFuture
    }

    fun prepareCosmeticRules() {
        compiledCosmeticRulesFuture
    }

    val isBundledBlockingReady: Boolean
        get() = bundledBlockingSnapshot.isReady

    fun onBundledBlockingReady(action: () -> Unit) {
        bundledBlockingSnapshot.onReady(action)
    }

    fun consentScriptIfReady(): String? = if (consentScriptFuture.isDone) {
        runCatching { consentScriptFuture.getNow("") }.getOrNull()?.takeIf(String::isNotEmpty)
    } else {
        null
    }

    fun onConsentScriptReady(action: (String) -> Unit) {
        consentScriptFuture.thenAccept { script ->
            if (script.isNotEmpty()) action(script)
        }
    }

    fun onCosmeticRulesReady(action: () -> Unit) {
        compiledCosmeticRulesFuture.thenRun(action)
    }

    @VisibleForTesting
    internal fun awaitCosmeticRulesForTesting() {
        compiledCosmeticRulesFuture.join()
    }

    fun shouldBlock(
        requestUrl: String,
        requestHost: String?,
        pageHost: String?,
    ): Boolean {
        if (!requestUrl.startsWith("http://", ignoreCase = true) &&
            !requestUrl.startsWith("https://", ignoreCase = true)
        ) return false
        val snapshot = bundledBlockingSnapshot.snapshot()
        val advancedDecision = snapshot.advancedRules.decideRequest(
            requestUrl = requestUrl,
            requestHost = requestHost,
            pageHost = pageHost,
        )
        return when (advancedDecision) {
            AdvancedFilterAction.Allow -> false
            AdvancedFilterAction.Block -> true
            null -> snapshot.requestBlocker.shouldBlockHosts(requestHost, pageHost)
        }
    }

    fun shouldBlock(requestUrl: String, pageUrl: String?): Boolean {
        val requestHost = runCatching { java.net.URI(requestUrl).host }.getOrNull()
        val pageHost = runCatching { java.net.URI(pageUrl).host }.getOrNull()
        return shouldBlock(requestUrl, requestHost, pageHost)
    }

    fun shouldBlockPopup(targetUrl: String, openerUrl: String?): Boolean =
        decidePopup(targetUrl, openerUrl) == AdvancedFilterAction.Block

    internal fun decidePopup(targetUrl: String, openerUrl: String?): AdvancedFilterAction? {
        val snapshot = bundledBlockingSnapshot.snapshot()
        snapshot.advancedRules.decidePopup(targetUrl, openerUrl)?.let { return it }
        val targetHost = runCatching { java.net.URI(targetUrl).host }.getOrNull()
        val openerHost = runCatching { java.net.URI(openerUrl).host }.getOrNull()
        return AdvancedFilterAction.Block.takeIf {
            snapshot.requestBlocker.shouldBlockHosts(targetHost, openerHost)
        }
    }

    internal fun decidePopunder(
        openerTargetUrl: String,
        childUrl: String?,
    ): AdvancedFilterAction? = bundledBlockingSnapshot.snapshot().advancedRules
        .decidePopunder(openerTargetUrl, childUrl)

    fun shouldBlockPopupWithoutTarget(openerUrl: String?): Boolean =
        bundledBlockingSnapshot.snapshot().advancedRules.shouldBlockPopupWithoutTarget(openerUrl)

    fun windowOpenDefuserScript(pageUrl: String?): String =
        CandyWindowOpenDefuserScript.script.takeIf {
            bundledBlockingSnapshot.snapshotIfReady()?.advancedRules
                ?.shouldDefuseWindowOpen(pageUrl) == true
        }.orEmpty()

    fun shouldBlockHosts(requestHost: String?, pageHost: String?): Boolean =
        bundledBlockingSnapshot.snapshot().requestBlocker.shouldBlockHosts(requestHost, pageHost)

    fun adCosmeticSelectors(pageUrl: String?): List<String> {
        val compiled = compiledCosmeticRulesIfReady()?.rules?.scopedSelectors(pageUrl).orEmpty()
        return (
            HIGH_CONFIDENCE_AD_SELECTORS +
                candyDefaultRules.adCosmeticSelectors(pageUrl) +
                compiled
        ).distinct()
    }

    internal fun genericCosmeticPayload(): String =
        compiledCosmeticRulesFuture.join().genericPayload.encoded

    internal fun genericCosmeticPolicyForHost(host: String?): String = runCatching {
        GenericCosmeticPolicyEncoding.encode(
            compiledCosmeticRulesFuture.join().rules.genericPolicyForHost(host),
        )
    }.getOrDefault(GenericCosmeticPolicyEncoding.DISABLED)

    fun genericCosmeticDocumentStartScript(
        pausedHosts: Collection<String> = emptyList(),
        bridgeToken: String,
    ): String = if (compiledCosmeticRulesIfReady() == null) {
        ""
    } else {
        GenericCosmeticScript.create(
            pausedHosts = pausedHosts,
            bridgeToken = bridgeToken,
        )
    }

    val genericCosmeticCleanupScript: String
        get() = GenericCosmeticScript.cleanupScript

    fun adCosmeticDocumentStartScript(
        pageUrl: String?,
        pausedHosts: Collection<String> = emptyList(),
    ): String = CandyCosmeticScript.create(
        selectors = adCosmeticSelectors(pageUrl),
        pausedHosts = pausedHosts,
    )

    fun adProceduralDocumentStartScript(pageUrl: String?): String =
        CandyProceduralCosmeticScript.create(
            bundledBlockingSnapshot.snapshotIfReady()?.proceduralRules
                ?.matchingRules(pageUrl).orEmpty(),
        )

    @VisibleForTesting
    internal fun awaitBundledBlockingForTesting() {
        bundledBlockingSnapshot.snapshot()
    }

    private fun compiledCosmeticRulesIfReady(): CompiledCosmeticRules? {
        val future = compiledCosmeticRulesFuture
        if (!future.isDone) return null
        return runCatching { future.getNow(null) }.getOrNull()
    }

    private fun readAssetOrEmpty(assetName: String): String = runCatching {
        appContext.assets.open(assetName).bufferedReader().use { it.readText() }
    }.getOrDefault("")

    private fun loadConsentCss(context: Context): ByteArray = ByteArrayOutputStream().use { output ->
        listOf("easylist_cookie_banner.css", "cookie_banner_overrides.css").forEach { assetName ->
            context.assets.open(assetName).use { it.copyTo(output) }
            output.write('\n'.code)
        }
        output.toByteArray()
    }

    private companion object {
        val HIGH_CONFIDENCE_AD_SELECTORS = listOf(
            ":is(img, iframe, embed)[src*=\"/ads_banner.\" i]",
            ":is(img, iframe, embed)[src*=\"_ads_banner.\" i]",
            "object[data*=\"/ads_banner.\" i]",
            "object[data*=\"_ads_banner.\" i]",
            "#ad_banner + #AlternateMessage",
        )
    }
}
