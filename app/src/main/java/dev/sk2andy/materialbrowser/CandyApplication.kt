package dev.sk2andy.materialbrowser

import android.app.Application
import dev.sk2andy.materialbrowser.browser.WebContentSafeAreaSession
import dev.sk2andy.materialbrowser.browser.guest.GuestBubbleGate
import dev.sk2andy.materialbrowser.browser.systemwebview.GuestBubbleWebView
import dev.sk2andy.materialbrowser.browser.guest.GuestBubbleRules
import dev.sk2andy.materialbrowser.data.AppLogging
import dev.sk2andy.materialbrowser.data.sync.MozillaSyncRepository
import dev.sk2andy.materialbrowser.browser.integration.devicesearch.DeviceSearchIndexer

class CandyApplication : Application() {
    internal val webContentSafeAreaSession = WebContentSafeAreaSession()

    override fun onCreate() {
        super.onCreate()
        if (getProcessName() == packageName + GuestBubbleRules.GUEST_PROCESS_SUFFIX) {
            GuestBubbleWebView.initializeProcessDirectory()
        }
        GuestBubbleGate.install(this)
        if (getProcessName() == packageName) {
            AppLogging.initialize(this)
            MozillaSyncRepository.install(this)
            DeviceSearchIndexer.install(this)
        }
    }
}
