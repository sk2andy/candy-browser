package dev.sk2andy.materialbrowser

import android.app.Application
import dev.sk2andy.materialbrowser.data.AppLogging

class CandyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (getProcessName() == packageName) AppLogging.initialize(this)
    }
}
