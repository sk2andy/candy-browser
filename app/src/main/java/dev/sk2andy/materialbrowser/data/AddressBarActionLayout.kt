package dev.sk2andy.materialbrowser.data

enum class AddressBarAction(val wireValue: String) {
    Favorite("favorite"),
    Pin("pin"),
    Desktop("desktop"),
    ForceVerticalScroll("force_vertical_scroll"),
    Reader("reader"),
    FindInPage("find_in_page"),
    Tabs("tabs"),
    Share("share"),
    Print("print"),
    NewTab("new_tab"),
    Reload("reload"),
    CloseTab("close_tab"),
    Back("back"),
    Forward("forward"),
    ParkRight("park_right"),
    ;

    companion object {
        fun fromWireValue(value: String?): AddressBarAction? =
            entries.firstOrNull { action -> action.wireValue == value }
    }
}

enum class AddressBarActionSide {
    BeforeAddress,
    AfterAddress,
}

data class AddressBarActionLayout(
    val beforeAddress: List<AddressBarAction>,
    val afterAddress: List<AddressBarAction>,
) {
    companion object {
        val Default = AddressBarActionLayout(
            beforeAddress = listOf(AddressBarAction.Tabs),
            afterAddress = listOf(AddressBarAction.NewTab),
        )
    }
}

internal object AddressBarActionLayoutRules {
    const val MAX_CONFIGURABLE_ACTIONS = 3

    fun normalize(layout: AddressBarActionLayout): AddressBarActionLayout {
        val accepted = mutableSetOf<AddressBarAction>()

        fun normalized(actions: List<AddressBarAction>): List<AddressBarAction> = buildList {
            for (action in actions) {
                if (accepted.size == MAX_CONFIGURABLE_ACTIONS) break
                if (accepted.add(action)) add(action)
            }
        }

        return AddressBarActionLayout(
            beforeAddress = normalized(layout.beforeAddress),
            afterAddress = normalized(layout.afterAddress),
        )
    }

    fun fromWireLists(
        beforeAddress: List<String?>?,
        afterAddress: List<String?>?,
    ): AddressBarActionLayout = normalize(
        AddressBarActionLayout(
            beforeAddress = beforeAddress.orEmpty().mapNotNull(AddressBarAction::fromWireValue),
            afterAddress = afterAddress.orEmpty().mapNotNull(AddressBarAction::fromWireValue),
        ),
    )

    fun available(layout: AddressBarActionLayout): List<AddressBarAction> {
        val normalized = normalize(layout)
        val placed = normalized.beforeAddress.toSet() + normalized.afterAddress
        return AddressBarAction.entries.filterNot(placed::contains)
    }

    fun reserveDynamicSlots(
        layout: AddressBarActionLayout,
        dynamicSlotCount: Int,
    ): AddressBarActionLayout {
        val normalized = normalize(layout)
        val slotsToRelease = dynamicSlotCount
            .coerceIn(0, MAX_CONFIGURABLE_ACTIONS)
        var before = normalized.beforeAddress
        var after = normalized.afterAddress
        while (before.size + after.size + slotsToRelease > MAX_CONFIGURABLE_ACTIONS) {
            if (after.isNotEmpty()) {
                after = after.dropLast(1)
            } else if (before.isNotEmpty()) {
                before = before.dropLast(1)
            } else {
                break
            }
        }
        return AddressBarActionLayout(before, after)
    }

    fun temporarilyHiddenActions(
        layout: AddressBarActionLayout,
        visibleLayout: AddressBarActionLayout,
    ): List<AddressBarAction> {
        val visible = normalize(visibleLayout).let { it.beforeAddress + it.afterAddress }.toSet()
        val normalized = normalize(layout)
        return (normalized.beforeAddress + normalized.afterAddress).filterNot(visible::contains)
    }

    fun insert(
        layout: AddressBarActionLayout,
        action: AddressBarAction,
        side: AddressBarActionSide,
        requestedIndex: Int,
    ): AddressBarActionLayout {
        val normalized = normalize(layout)
        val placed = normalized.beforeAddress + normalized.afterAddress
        if (action in placed || placed.size == MAX_CONFIGURABLE_ACTIONS) return normalized
        return normalized.withInserted(action, side, requestedIndex)
    }

    fun move(
        layout: AddressBarActionLayout,
        action: AddressBarAction,
        side: AddressBarActionSide,
        requestedIndex: Int,
    ): AddressBarActionLayout {
        val normalized = normalize(layout)
        if (action !in normalized.beforeAddress && action !in normalized.afterAddress) {
            return normalized
        }
        return normalized
            .without(action)
            .withInserted(action, side, requestedIndex)
    }

    fun remove(
        layout: AddressBarActionLayout,
        action: AddressBarAction,
    ): AddressBarActionLayout = normalize(layout).without(action)

    private fun AddressBarActionLayout.withInserted(
        action: AddressBarAction,
        side: AddressBarActionSide,
        requestedIndex: Int,
    ): AddressBarActionLayout = when (side) {
        AddressBarActionSide.BeforeAddress -> copy(
            beforeAddress = beforeAddress.withInserted(action, requestedIndex),
        )
        AddressBarActionSide.AfterAddress -> copy(
            afterAddress = afterAddress.withInserted(action, requestedIndex),
        )
    }

    private fun AddressBarActionLayout.without(
        action: AddressBarAction,
    ): AddressBarActionLayout = copy(
        beforeAddress = beforeAddress.filterNot { it == action },
        afterAddress = afterAddress.filterNot { it == action },
    )

    private fun List<AddressBarAction>.withInserted(
        action: AddressBarAction,
        requestedIndex: Int,
    ): List<AddressBarAction> = toMutableList().apply {
        add(requestedIndex.coerceIn(0, size), action)
    }
}
