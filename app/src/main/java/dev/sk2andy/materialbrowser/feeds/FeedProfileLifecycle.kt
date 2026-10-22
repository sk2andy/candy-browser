package dev.sk2andy.materialbrowser.feeds

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object FeedProfileLifecycle {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun remove(context: Context, profileId: String) {
        val appContext = context.applicationContext
        FeedPlaybackManager.get(appContext).invalidate(profileId)
        FeedRepository.invalidate(setOf(profileId))
        WorkManager.getInstance(appContext).cancelUniqueWork("feeds-refresh-$profileId")
        WorkManager.getInstance(appContext).cancelUniqueWork("feeds-downloads-$profileId")
        NotificationManagerCompat.from(appContext).cancel("feeds-$profileId", 1)
        scope.launch {
            FeedRepository.deleteProfile(appContext, profileId).onFailure {
                Log.e("CandyFeeds", "Profile cleanup failed", it)
            }
        }
    }
}
