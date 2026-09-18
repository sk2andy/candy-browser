package dev.sk2andy.materialbrowser.ui

import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.ContextThemeWrapper
import android.view.View
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import dev.sk2andy.materialbrowser.R

@Composable
internal fun CastRouteButton(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val contentDescription = stringResource(R.string.cd_cast_video)
    val tintPaint = remember(tint) {
        Paint().apply {
            colorFilter = PorterDuffColorFilter(tint.toArgb(), PorterDuff.Mode.SRC_IN)
        }
    }
    AndroidView(
        factory = { context ->
            MediaRouteButton(
                ContextThemeWrapper(context, R.style.Theme_MaterialBrowser_MediaRouteButton),
            ).apply {
                CastButtonFactory.setUpMediaRouteButton(context, this)
                this.contentDescription = contentDescription
            }
        },
        update = {
            it.contentDescription = contentDescription
            it.setLayerType(View.LAYER_TYPE_HARDWARE, tintPaint)
        },
        modifier = modifier
            .size(48.dp)
            .testTag(CastControlsTestTags.RouteButton),
    )
}
