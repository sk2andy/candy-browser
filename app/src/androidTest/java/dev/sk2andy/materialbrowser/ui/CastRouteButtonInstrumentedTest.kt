package dev.sk2andy.materialbrowser.ui

import android.view.ContextThemeWrapper
import android.view.ViewGroup
import androidx.mediarouter.app.MediaRouteButton
import androidx.mediarouter.app.MediaRouteChooserDialogFragment
import androidx.mediarouter.media.MediaRouteSelector
import androidx.test.core.app.ActivityScenario
import com.google.android.gms.cast.CastMediaControlIntent
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.R
import org.junit.Assert.assertTrue
import org.junit.Test

class CastRouteButtonInstrumentedTest {
    @Test
    fun mediaRouteDialogOpensFromMainActivity() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val button = MediaRouteButton(
                    ContextThemeWrapper(
                        activity,
                        R.style.Theme_MaterialBrowser_MediaRouteButton,
                    ),
                )
                button.routeSelector = MediaRouteSelector.Builder()
                    .addControlCategory(
                        CastMediaControlIntent.categoryForCast(
                            CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID,
                        ),
                    )
                    .build()
                activity.addContentView(
                    button,
                    ViewGroup.LayoutParams(1, 1),
                )

                assertTrue(button.showDialog())
                activity.supportFragmentManager.executePendingTransactions()
                assertTrue(
                    activity.supportFragmentManager.fragments.any {
                        it is MediaRouteChooserDialogFragment
                    },
                )
            }
        }
    }
}
