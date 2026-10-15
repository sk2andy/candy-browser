package dev.sk2andy.materialbrowser

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import dev.sk2andy.materialbrowser.capsule.CapsuleIntentRules
import dev.sk2andy.materialbrowser.capsule.CapsuleWindowRules
import dev.sk2andy.materialbrowser.capsule.CapsuleWindowLauncher
import dev.sk2andy.materialbrowser.capsule.SiteCapsuleRules
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.SiteCapsuleStore

/** Resolve current Capsule options at launch, including shortcuts published before an edit. */
class SiteCapsuleLauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val capsuleId = SiteCapsuleRules.opaqueId(
            intent.getStringExtra(CapsuleIntentRules.EXTRA_CAPSULE_ID),
        )
        val capsule = SiteCapsuleStore(this).load().firstOrNull { it.id == capsuleId }
        val target = if (capsule != null && CapsuleWindowRules.canOpen(capsule, BrowserSessionStore(this).loadProfiles().first)) {
            CapsuleWindowLauncher.intent(this, capsule)
        } else {
            Intent(this, MainActivity::class.java)
                .setAction(CapsuleIntentRules.ACTION_OPEN_CAPSULE)
                .putExtra(CapsuleIntentRules.EXTRA_CAPSULE_ID, capsule?.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(target)
        finish()
    }
}
