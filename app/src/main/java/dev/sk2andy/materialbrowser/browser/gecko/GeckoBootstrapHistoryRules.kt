package dev.sk2andy.materialbrowser.browser.gecko

import kotlin.math.abs

internal object GeckoBootstrapHistoryRules {
    fun canRestoreHistory(urls: List<String>): Boolean =
        urls.none(CandyPrivacyHostContract::isBootstrapDocumentUrl)

    fun historyIndexAtOffset(urls: List<String>, currentIndex: Int, offset: Int): Int? {
        if (currentIndex !in urls.indices) return null
        if (offset == 0) {
            return currentIndex.takeUnless { CandyPrivacyHostContract.isBootstrapDocumentUrl(urls[it]) }
        }
        val direction = if (offset > 0) 1 else -1
        var remaining = abs(offset.toLong())
        var index = currentIndex + direction
        while (index in urls.indices) {
            if (!CandyPrivacyHostContract.isBootstrapDocumentUrl(urls[index])) {
                remaining -= 1
                if (remaining == 0L) return index
            }
            index += direction
        }
        return null
    }
}
