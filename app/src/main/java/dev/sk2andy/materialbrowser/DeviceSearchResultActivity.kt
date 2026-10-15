package dev.sk2andy.materialbrowser

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import dev.sk2andy.materialbrowser.browser.integration.devicesearch.DeviceSearchContract

/** Launcher deeplink boundary. Main browser revalidates consent and the current saved source. */
class DeviceSearchResultActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val itemId = DeviceSearchContract.itemId(intent)
        if (itemId != null) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .setAction(DeviceSearchContract.ACTION_OPEN)
                    .putExtra(DeviceSearchContract.EXTRA_ITEM_ID, itemId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        }
        finish()
    }
}
