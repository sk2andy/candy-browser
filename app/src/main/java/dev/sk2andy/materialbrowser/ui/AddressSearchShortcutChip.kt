@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.sk2andy.materialbrowser.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R

@Composable
internal fun AddressSearchShortcutChip(
    keyword: String?,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = keyword,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(motionScheme.fastEffectsSpec()) +
                slideInHorizontally(motionScheme.fastSpatialSpec()) { -it / 2 })
                .togetherWith(
                    fadeOut(motionScheme.fastEffectsSpec()) +
                        slideOutHorizontally(motionScheme.fastSpatialSpec()) { -it / 2 },
                )
                .using(SizeTransform { _, _ -> motionScheme.fastSpatialSpec() })
        },
        contentAlignment = Alignment.CenterStart,
        label = "Address search shortcut",
    ) { displayedKeyword ->
        if (displayedKeyword != null) {
            val description = stringResource(
                R.string.search_shortcut_chip_remove,
                displayedKeyword,
            )
            Surface(
                onClick = onRemove,
                enabled = keyword != null,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .testTag(AddressSearchShortcutTestTags.Chip)
                    .semantics { contentDescription = description },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "!$displayedKeyword",
                        modifier = Modifier.widthIn(max = 112.dp),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 4.dp).size(16.dp),
                    )
                }
            }
        }
    }
}

internal object AddressSearchShortcutTestTags {
    const val Chip = "address_search_shortcut_chip"
}
