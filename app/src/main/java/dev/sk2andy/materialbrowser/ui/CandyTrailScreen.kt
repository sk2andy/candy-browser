package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.CandyTrail
import dev.sk2andy.materialbrowser.shared.ui.CandyTrailHaptic
import dev.sk2andy.materialbrowser.shared.ui.CandyTrailScreenContent
import dev.sk2andy.materialbrowser.shared.ui.CandyTrailStrings
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor

/** Android resources, bitmaps and haptics for shared Candy Trail renderer. */
@Composable
internal fun CandyTrailScreen(
    tab: BrowserTab,
    trail: CandyTrail,
    favicon: Bitmap?,
    forkFavicons: Map<String, Bitmap>,
    predictiveBackProgress: Float,
    predictiveBackEdgeSign: Int,
    onSelectNode: (String) -> Boolean,
    onNodeSelectionFinished: () -> Unit,
    onForkNode: (String) -> String?,
    onForkCreationFinished: (String) -> Unit,
    onSelectFork: (String) -> String?,
    onForkSelectionFinished: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    pageFavicons: Map<String, Bitmap> = emptyMap(),
) {
    val locale = LocalConfiguration.current.locales[0]
    val currentNodeDescription = stringResource(R.string.cd_candy_trail_current_node)
    val nodeDescription = stringResource(R.string.cd_candy_trail_node)
    val nodeActionsDescription = stringResource(R.string.cd_candy_trail_node_actions)
    val openForkStatus = stringResource(R.string.fork_status_open)
    val closedForkStatus = stringResource(R.string.fork_status_closed)
    val openForkDescription = stringResource(R.string.cd_candy_trail_fork_open)
    val closedForkDescription = stringResource(R.string.cd_candy_trail_fork_closed)
    val hiddenPages = stringResource(R.string.candy_trail_hidden_pages)
    val rootView = LocalView.current
    val strings = CandyTrailStrings(
        empty = stringResource(R.string.candy_trail_empty),
        nodeDescription = { isCurrent, title, host ->
            (if (isCurrent) currentNodeDescription else nodeDescription).format(locale, title, host)
        },
        nodeActionsDescription = { title ->
            nodeActionsDescription.format(locale, title)
        },
        forkStatus = { isOpen ->
            if (isOpen) openForkStatus else closedForkStatus
        },
        forkDescription = { isOpen, title, host ->
            (if (isOpen) openForkDescription else closedForkDescription).format(locale, title, host)
        },
        forkUrlOnlyDisclaimer = stringResource(R.string.fork_url_only_disclaimer),
        forkFromHere = stringResource(R.string.action_fork_from_here),
        close = stringResource(R.string.cd_close_candy_trail),
        title = stringResource(R.string.candy_trail_title),
        newTabTitle = stringResource(R.string.new_tab_title),
        zoomOut = stringResource(R.string.cd_zoom_out),
        resetZoom = stringResource(R.string.action_reset_zoom),
        zoomIn = stringResource(R.string.cd_zoom_in),
        fitAll = stringResource(R.string.candy_trail_fit_all),
        currentPage = stringResource(R.string.candy_trail_current_page),
        graphView = stringResource(R.string.candy_trail_graph_view),
        listView = stringResource(R.string.candy_trail_list_view),
        focusRoute = stringResource(R.string.candy_trail_focus_route),
        allBranches = stringResource(R.string.candy_trail_all_branches),
        collapseBranch = stringResource(R.string.candy_trail_collapse_branch),
        expandBranch = stringResource(R.string.candy_trail_expand_branch),
        hiddenPages = { count -> hiddenPages.format(locale, count) },
    )
    CandyTrailScreenContent(
        tab = tab,
        trail = trail,
        favicon = favicon?.takeUnless(Bitmap::isRecycled)?.asImageBitmap(),
        forkFavicons = forkFavicons.mapNotNull { (tabId, bitmap) ->
            bitmap.takeUnless(Bitmap::isRecycled)?.asImageBitmap()?.let { tabId to it }
        }.toMap(),
        pageFavicons = pageFavicons.mapNotNull { (url, bitmap) ->
            bitmap.takeUnless(Bitmap::isRecycled)?.asImageBitmap()?.let { url to it }
        }.toMap(),
        strings = strings,
        nodeActionsContainerColor = browserChromeColor(
            MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        onSelectNode = onSelectNode,
        onNodeSelectionFinished = onNodeSelectionFinished,
        onForkNode = onForkNode,
        onForkCreationFinished = onForkCreationFinished,
        onSelectFork = onSelectFork,
        onForkSelectionFinished = onForkSelectionFinished,
        onDismiss = onDismiss,
        onHaptic = { haptic ->
            when (haptic) {
                CandyTrailHaptic.Confirm -> rootView.performConfirmHaptic()
                CandyTrailHaptic.VirtualKey -> rootView.performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY,
                )
            }
        },
        platformSurfaceModifier = Modifier.predictiveBackSurface(
            progress = predictiveBackProgress,
            swipeEdgeSign = predictiveBackEdgeSign,
        ),
        modifier = modifier,
    )
}
