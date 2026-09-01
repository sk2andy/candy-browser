package dev.sk2andy.materialbrowser.ui

import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import dev.sk2andy.materialbrowser.R

@Composable
internal fun CastRouteButton(modifier: Modifier = Modifier) {
    val contentDescription = stringResource(R.string.cd_cast_video)
    AndroidView(
        factory = { context ->
            MediaRouteButton(
                ContextThemeWrapper(context, R.style.Theme_MaterialBrowser_MediaRouteButton),
            ).apply {
                CastButtonFactory.setUpMediaRouteButton(context, this)
                this.contentDescription = contentDescription
            }
        },
        update = { it.contentDescription = contentDescription },
        modifier = modifier
            .size(48.dp)
            .testTag(CastControlsTestTags.RouteButton),
    )
}
