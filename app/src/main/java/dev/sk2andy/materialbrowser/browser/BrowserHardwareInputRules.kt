package dev.sk2andy.materialbrowser.browser

internal enum class BrowserHardwareInputAction {
    FocusAddress,
    NewTab,
    CloseTab,
    Reload,
    FindInPage,
    PreviousTab,
    NextTab,
    GoBack,
    GoForward,
}

internal enum class BrowserHardwareKey {
    L,
    T,
    W,
    R,
    F,
    Tab,
    Left,
    Right,
    F5,
    Other,
}

internal enum class BrowserMouseButton {
    Back,
    Forward,
    Other,
}

internal data class BrowserHardwareKeyStroke(
    val key: BrowserHardwareKey,
    val ctrlPressed: Boolean = false,
    val metaPressed: Boolean = false,
    val altPressed: Boolean = false,
    val shiftPressed: Boolean = false,
    val repeatCount: Int = 0,
)

internal object BrowserHardwareInputRules {
    fun keyboardAction(stroke: BrowserHardwareKeyStroke): BrowserHardwareInputAction? {
        if (stroke.repeatCount != 0) return null

        val primaryModifierPressed = stroke.ctrlPressed.xor(stroke.metaPressed)
        val primaryModifierAbsent = !stroke.ctrlPressed && !stroke.metaPressed
        return when {
            primaryModifierPressed && !stroke.altPressed && !stroke.shiftPressed -> when (stroke.key) {
                BrowserHardwareKey.L -> BrowserHardwareInputAction.FocusAddress
                BrowserHardwareKey.T -> BrowserHardwareInputAction.NewTab
                BrowserHardwareKey.W -> BrowserHardwareInputAction.CloseTab
                BrowserHardwareKey.R -> BrowserHardwareInputAction.Reload
                BrowserHardwareKey.F -> BrowserHardwareInputAction.FindInPage
                BrowserHardwareKey.Tab -> BrowserHardwareInputAction.NextTab
                else -> null
            }
            primaryModifierPressed &&
                !stroke.altPressed &&
                stroke.shiftPressed &&
                stroke.key == BrowserHardwareKey.Tab -> BrowserHardwareInputAction.PreviousTab
            stroke.altPressed &&
                primaryModifierAbsent &&
                !stroke.shiftPressed &&
                stroke.key == BrowserHardwareKey.Left -> BrowserHardwareInputAction.GoBack
            stroke.altPressed &&
                primaryModifierAbsent &&
                !stroke.shiftPressed &&
                stroke.key == BrowserHardwareKey.Right -> BrowserHardwareInputAction.GoForward
            primaryModifierAbsent &&
                !stroke.altPressed &&
                !stroke.shiftPressed &&
                stroke.key == BrowserHardwareKey.F5 -> BrowserHardwareInputAction.Reload
            else -> null
        }
    }

    fun mouseAction(button: BrowserMouseButton): BrowserHardwareInputAction? = when (button) {
        BrowserMouseButton.Back -> BrowserHardwareInputAction.GoBack
        BrowserMouseButton.Forward -> BrowserHardwareInputAction.GoForward
        BrowserMouseButton.Other -> null
    }

    fun adjacentTabId(
        tabIds: List<String>,
        selectedTabId: String,
        forward: Boolean,
    ): String? {
        if (tabIds.size < 2) return null
        val selectedIndex = tabIds.indexOf(selectedTabId)
        if (selectedIndex < 0) return null
        val targetIndex = if (forward) {
            (selectedIndex + 1) % tabIds.size
        } else {
            (selectedIndex - 1 + tabIds.size) % tabIds.size
        }
        return tabIds[targetIndex]
    }
}
