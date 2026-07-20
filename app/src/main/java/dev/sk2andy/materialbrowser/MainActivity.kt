package dev.sk2andy.materialbrowser

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.integration.IncomingBrowserIntent
import dev.sk2andy.materialbrowser.ui.BrowserScreen
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme

class MainActivity : ComponentActivity() {
    private lateinit var browserController: BrowserController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        browserController = BrowserController(this)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            browserController.onWindowInsetsChanged(insets)
            insets
        }
        if (savedInstanceState == null) openIntentUrl(intent)
        setContent {
            MaterialBrowserTheme {
                BrowserScreen(browserController)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openIntentUrl(intent)
    }

    override fun onPause() {
        browserController.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::browserController.isInitialized) browserController.onResume()
    }

    override fun onDestroy() {
        if (::browserController.isInitialized) browserController.destroy()
        super.onDestroy()
    }

    private fun openIntentUrl(intent: Intent) {
        IncomingBrowserIntent.from(intent)?.let { request ->
            browserController.openUrl(request.url)
        }
    }
}
