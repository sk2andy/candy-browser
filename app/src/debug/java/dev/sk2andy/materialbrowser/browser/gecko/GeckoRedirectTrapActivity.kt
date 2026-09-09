package dev.sk2andy.materialbrowser.browser.gecko

import android.app.Activity
import android.os.Bundle
import java.util.concurrent.atomic.AtomicInteger

/** Debug-only non-browser app-link target used to catch premature same-site handoffs. */
internal class GeckoRedirectTrapActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchCount.incrementAndGet()
        finish()
    }

    companion object {
        private val launchCount = AtomicInteger()

        fun launchCount(): Int = launchCount.get()

        fun reset() {
            launchCount.set(0)
        }
    }
}
