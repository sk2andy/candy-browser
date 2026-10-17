package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

data class LibrarySearchBarState(
    val query: String,
    val placeholder: String,
    val clearContentDescription: String,
    val testTag: String,
    val onQueryChange: (String) -> Unit,
)

/** Android's host compiles its Material SearchBar against the app's resolved Material version. */
interface LibrarySearchBarRenderer {
    @Composable
    fun searchBar(state: LibrarySearchBarState)
}

val LocalLibrarySearchBarRenderer = staticCompositionLocalOf<LibrarySearchBarRenderer?> { null }

@Composable
fun LibrarySearchBar(
    query: String,
    placeholder: String,
    clearContentDescription: String,
    testTag: String,
    onQueryChange: (String) -> Unit,
    renderer: LibrarySearchBarRenderer? = LocalLibrarySearchBarRenderer.current,
) {
    val effects = LocalCandyBrowserChromeEffects.current
    if (!effects.usesGroupedSettings && renderer != null) {
        renderer.searchBar(LibrarySearchBarState(query, placeholder, clearContentDescription, testTag, onQueryChange))
    } else {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text(placeholder) },
            leadingIcon = {
                effects.icon("magnifyingglass", Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        effects.icon(
                            "xmark.circle.fill",
                            Icons.Default.Close,
                            contentDescription = clearContentDescription,
                        )
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag(testTag),
        )
    }
}
