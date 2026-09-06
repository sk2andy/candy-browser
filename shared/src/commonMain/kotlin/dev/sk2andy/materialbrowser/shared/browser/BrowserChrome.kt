package dev.sk2andy.materialbrowser.shared.browser

data class BrowserChromeState(
    val address: String = "",
    val pageTitle: String = "Candy",
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isLoading: Boolean = false,
)

enum class BrowserChromeAction {
    Navigate,
    Back,
    Forward,
    Reload,
    Stop,
}

class BrowserChromeController {
    var state: BrowserChromeState
        private set

    constructor() : this(BrowserChromeState())

    constructor(initialState: BrowserChromeState) {
        state = initialState
    }

    fun editAddress(value: String): BrowserChromeState = update(state.copy(address = value))

    fun navigationStarted(): BrowserChromeState = update(state.copy(isLoading = true))

    fun navigationFinished(
        address: String,
        pageTitle: String,
        canGoBack: Boolean,
        canGoForward: Boolean,
    ): BrowserChromeState = update(
        state.copy(
            address = address,
            pageTitle = pageTitle.takeIf(String::isNotBlank) ?: "Candy",
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            isLoading = false,
        ),
    )

    fun reloadAction(): BrowserChromeAction = if (state.isLoading) {
        BrowserChromeAction.Stop
    } else {
        BrowserChromeAction.Reload
    }

    private fun update(value: BrowserChromeState): BrowserChromeState {
        state = value
        return value
    }
}
