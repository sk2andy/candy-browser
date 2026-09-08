package dev.sk2andy.materialbrowser.browser.gecko

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Debug-only result target for the Gecko WebAuthn Activity launcher contract. */
internal class GeckoWebAuthnResultTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(
            RESULT_OK,
            Intent().putExtra(EXTRA_RESULT, RESULT_VALUE),
        )
        finish()
    }

    companion object {
        const val EXTRA_RESULT = "geckoWebAuthnResult"
        const val RESULT_VALUE = "success"
    }
}
