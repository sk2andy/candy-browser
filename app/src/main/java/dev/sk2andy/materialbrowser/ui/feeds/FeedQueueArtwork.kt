package dev.sk2andy.materialbrowser.ui.feeds

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.data.FaviconRepository
import dev.sk2andy.materialbrowser.data.FavoriteFaviconRepository
import dev.sk2andy.materialbrowser.feeds.FeedArticle
import dev.sk2andy.materialbrowser.feeds.FeedRepository
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Composable
internal fun rememberFeedQueueFavicon(repository: FeedRepository, article: FeedArticle?): Bitmap? {
    val context = LocalContext.current.applicationContext
    val url = article?.url
    var favicon by remember(repository, url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(repository, url) {
        if (repository.isPrivate || url == null) return@LaunchedEffect
        try {
            favicon =
                withContext(Dispatchers.IO) {
                    repository.requireAccess()
                    val cached = FavoriteFaviconRepository.get(context).loadAll(listOf(url))[url]
                    val bitmap =
                        cached
                            ?: suspendCancellableCoroutine<Bitmap?> { continuation ->
                                val accepted =
                                    FaviconRepository.get(context)
                                        .fetch(
                                            pageUrl = url,
                                            shouldFetch = { continuation.isActive },
                                            onLoaded = { loaded ->
                                                if (continuation.isActive)
                                                    continuation.resume(loaded)
                                            },
                                        )
                                if (!accepted) continuation.resume(null)
                            }
                    repository.requireAccess()
                    bitmap
                }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A missing icon must never prevent listening to an article.
        }
    }
    return favicon
}

@Composable
internal fun FeedQueueArtwork(favicon: Bitmap?, source: String) {
    Surface(
        modifier = Modifier.size(64.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        val availableIcon = favicon?.takeUnless(Bitmap::isRecycled)
        if (availableIcon != null) {
            Image(
                bitmap = availableIcon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.padding(12.dp).testTag("feeds_queue_favicon"),
                contentScale = ContentScale.Fit,
            )
        } else {
            Box(
                modifier = Modifier.testTag("feeds_queue_favicon_fallback").clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = source.firstOrNull(Char::isLetterOrDigit)?.uppercase() ?: "C",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
