package dev.sk2andy.materialbrowser.shared.browser

data class NavigationState(
    val currentUrl: BrowserUrl? = null,
    val title: String = "",
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isLoading: Boolean = false,
)

sealed interface NavigationEvent {
    data class Started(val url: BrowserUrl) : NavigationEvent

    data class Committed(
        val url: BrowserUrl,
        val title: String,
        val canGoBack: Boolean,
        val canGoForward: Boolean,
    ) : NavigationEvent

    data class Failed(val fallbackUrl: BrowserUrl?) : NavigationEvent
}

object NavigationRules {
    private const val MAX_TITLE_LENGTH = 4_096

    fun reduce(state: NavigationState, event: NavigationEvent): NavigationState = when (event) {
        is NavigationEvent.Started -> state.copy(
            currentUrl = event.url,
            isLoading = true,
        )
        is NavigationEvent.Committed -> state.copy(
            currentUrl = event.url,
            title = event.title.take(MAX_TITLE_LENGTH),
            canGoBack = event.canGoBack,
            canGoForward = event.canGoForward,
            isLoading = false,
        )
        is NavigationEvent.Failed -> state.copy(
            currentUrl = event.fallbackUrl ?: state.currentUrl,
            isLoading = false,
        )
    }
}
