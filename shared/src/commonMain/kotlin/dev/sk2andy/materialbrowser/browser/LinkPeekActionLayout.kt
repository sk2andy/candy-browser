package dev.sk2andy.materialbrowser.browser

enum class LinkPeekAction(val wireValue: String) {
    ReaderLater("reader_later"),
    OpenPrivate("open_private"),
    Copy("copy"),
    Share("share"),
    Favorite("favorite"),
    Snooze("snooze"),
    OpenForeground("open_foreground"),
    ;

    companion object {
        fun fromWireValue(value: String?): LinkPeekAction? =
            entries.firstOrNull { action -> action.wireValue == value }
    }
}

data class LinkPeekActionLayout(
    val actions: List<LinkPeekAction?>,
) {
    companion object {
        val Default = LinkPeekActionLayout(
            actions = listOf(
                LinkPeekAction.Copy,
                LinkPeekAction.OpenPrivate,
                LinkPeekAction.Share,
            ),
        )
    }
}

sealed interface LinkPeekActionSlot {
    data class Action(
        val action: LinkPeekAction,
        val actionIndex: Int,
    ) : LinkPeekActionSlot

    data class Empty(
        val actionIndex: Int,
    ) : LinkPeekActionSlot

    data object FixedPlus : LinkPeekActionSlot
}

object LinkPeekActionLayoutRules {
    const val CONFIGURABLE_ACTION_COUNT = 3
    const val TOOLBAR_SLOT_COUNT = 4
    const val FIXED_PLUS_SLOT_INDEX = 2

    fun normalize(layout: LinkPeekActionLayout): LinkPeekActionLayout {
        val accepted = mutableSetOf<LinkPeekAction>()
        val normalized = List(CONFIGURABLE_ACTION_COUNT) { index ->
            layout.actions.getOrNull(index)?.takeIf(accepted::add)
        }
        return LinkPeekActionLayout(normalized)
    }

    fun fromWireValues(values: List<String?>?): LinkPeekActionLayout = normalize(
        LinkPeekActionLayout(
            actions = values.orEmpty().map(LinkPeekAction::fromWireValue),
        ),
    )

    fun available(layout: LinkPeekActionLayout): List<LinkPeekAction> {
        val selected = normalize(layout).actions.toSet()
        return LinkPeekAction.entries.filterNot(selected::contains)
    }

    fun place(
        layout: LinkPeekActionLayout,
        action: LinkPeekAction,
        targetActionIndex: Int,
    ): LinkPeekActionLayout {
        val normalized = normalize(layout)
        if (targetActionIndex !in 0 until CONFIGURABLE_ACTION_COUNT) return normalized

        val actions = normalized.actions.toMutableList()
        if (actions[targetActionIndex] != null) return normalized
        val sourceIndex = actions.indexOf(action)
        if (sourceIndex >= 0) {
            actions[sourceIndex] = null
        }
        actions[targetActionIndex] = action
        return LinkPeekActionLayout(actions)
    }

    fun remove(
        layout: LinkPeekActionLayout,
        action: LinkPeekAction,
    ): LinkPeekActionLayout {
        val normalized = normalize(layout)
        return normalized.copy(
            actions = normalized.actions.map { current ->
                current.takeUnless { it == action }
            },
        )
    }

    fun runtimeSlots(layout: LinkPeekActionLayout): List<LinkPeekActionSlot> {
        val actions = normalize(layout).actions
        return listOf(
            actions[0].toRuntimeSlot(actionIndex = 0),
            actions[1].toRuntimeSlot(actionIndex = 1),
            LinkPeekActionSlot.FixedPlus,
            actions[2].toRuntimeSlot(actionIndex = 2),
        )
    }

    private fun LinkPeekAction?.toRuntimeSlot(actionIndex: Int): LinkPeekActionSlot =
        this?.let { action -> LinkPeekActionSlot.Action(action, actionIndex) }
            ?: LinkPeekActionSlot.Empty(actionIndex)
}
