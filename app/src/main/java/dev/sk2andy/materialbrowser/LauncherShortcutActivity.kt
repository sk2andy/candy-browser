package dev.sk2andy.materialbrowser

import android.app.Activity
import android.os.Bundle
import dev.sk2andy.materialbrowser.browser.integration.LauncherShortcutPublisher
import dev.sk2andy.materialbrowser.browser.integration.LauncherShortcutRules
import dev.sk2andy.materialbrowser.browser.integration.LauncherShortcutTarget

class LauncherShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = when (intent.action) {
            LauncherShortcutRules.ACTION_NEW_TAB -> LauncherShortcutTarget.NewTab
            LauncherShortcutRules.ACTION_NEW_PRIVATE_TAB ->
                LauncherShortcutTarget.NewPrivateTab
            LauncherShortcutRules.ACTION_OPEN_PROFILE ->
                intent.getStringExtra(LauncherShortcutRules.EXTRA_PROFILE_ID)
                    ?.let(LauncherShortcutTarget::Profile)
            else -> null
        }
        if (target != null) {
            startActivity(LauncherShortcutPublisher(this).launchIntent(target))
        }
        finish()
    }
}
