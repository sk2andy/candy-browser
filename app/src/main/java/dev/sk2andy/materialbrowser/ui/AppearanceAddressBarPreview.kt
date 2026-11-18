package dev.sk2andy.materialbrowser.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.data.AddressBarDockEdge
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserBackdropBlurMode
import dev.sk2andy.materialbrowser.browser.BrowserBackdropBlurRules
import dev.sk2andy.materialbrowser.ui.theme.BrowserChromeSurfaceRole
import dev.sk2andy.materialbrowser.ui.theme.browserChromeSurfaceTokens
import eightbitlab.com.blurview.BlurTarget

internal object AppearancePreviewTestTags {
    const val Preview = "appearance_address_bar_preview"
}

@Composable
internal fun AppearanceAddressBarPreview(browserEngineKind: AndroidBrowserEngineKind) {
    var target by remember { mutableStateOf<BlurTarget?>(null) }
    val blurAvailable = BrowserBackdropBlurRules.mode(browserEngineKind, Build.VERSION.SDK_INT) !=
        BrowserBackdropBlurMode.Unavailable
    val addressTokens = browserChromeSurfaceTokens(BrowserChromeSurfaceRole.AddressBar)
    val chromeTokens = browserChromeSurfaceTokens()
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .clearAndSetSemantics { testTag = AppearancePreviewTestTags.Preview },
    ) {
        BrowserContentBlurTarget(
            enabled = blurAvailable,
            onTargetAttached = { target = it },
            onTargetReleased = { if (target === it) target = null },
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize().background(colors.surfaceContainerHighest)) {
                Box(Modifier.fillMaxWidth().height(52.dp).background(colors.primaryContainer))
                Box(
                    Modifier.align(Alignment.CenterStart).offset(x = 30.dp)
                        .size(100.dp, 70.dp).background(colors.tertiary, RoundedCornerShape(18.dp)),
                )
                Box(
                    Modifier.align(Alignment.CenterEnd).offset(x = (-24).dp)
                        .size(130.dp, 60.dp).background(colors.secondary, RoundedCornerShape(18.dp)),
                )
            }
        }
        CandyChromeSurface(
            backdropSource = target.takeIf { blurAvailable }.asCandyChromeBackdropSource(),
            tokens = chromeTokens,
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.MoreVert, contentDescription = null)
            }
        }
        CandyChromeSurface(
            backdropSource = target.takeIf { blurAvailable }.asCandyChromeBackdropSource(),
            tokens = addressTokens,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
                .width(176.dp).height(48.dp),
        ) {
            AddressBarCompactContent(
                domain = "example.com",
                showCastButton = false,
                dockingEnabled = false,
                dockTargetEdge = AddressBarDockEdge.Right,
                onDock = {},
            )
        }
    }
}
