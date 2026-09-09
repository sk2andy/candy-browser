package dev.sk2andy.materialbrowser.browser.gecko

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Debug-only result target for the Gecko WebAuthn Activity launcher contract. */
internal class GeckoWebAuthnResultTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.postDelayed(
            {
                setResult(
                    RESULT_OK,
                    Intent()
                        .putExtra(EXTRA_RESULT, RESULT_VALUE)
                        .takeUnless { intent.getBooleanExtra(EXTRA_NULL_RESULT, false) },
                )
                finish()
            },
            intent.getLongExtra(EXTRA_DELAY_MILLIS, 0L).coerceIn(0L, MAX_DELAY_MILLIS),
        )
    }

    companion object {
        const val EXTRA_DELAY_MILLIS = "geckoWebAuthnDelayMillis"
        const val EXTRA_NULL_RESULT = "geckoWebAuthnNullResult"
        const val EXTRA_RESULT = "geckoWebAuthnResult"
        const val RESULT_VALUE = "success"
        private const val MAX_DELAY_MILLIS = 5_000L
    }
}
