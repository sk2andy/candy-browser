package dev.sk2andy.materialbrowser

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.SystemClock
import android.widget.Toast
import dev.sk2andy.materialbrowser.browser.integration.ProcessTextRules
import dev.sk2andy.materialbrowser.browser.integration.processTextSpeechRequests

internal fun Activity.startSelectedTextSpeech(text: String) {
    val token = processTextSpeechRequests.put(text, SystemClock.elapsedRealtime()) ?: return
    val launched = try {
        startActivity(
            Intent(this, ReadSelectedTextActivity::class.java)
                .setAction(ProcessTextRules.ACTION_READ_ALOUD)
                .putExtra(ProcessTextRules.EXTRA_REQUEST_TOKEN, token),
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
    if (!launched) {
        processTextSpeechRequests.take(token, SystemClock.elapsedRealtime())
        Toast.makeText(this, R.string.process_text_unavailable, Toast.LENGTH_LONG).show()
    }
}
