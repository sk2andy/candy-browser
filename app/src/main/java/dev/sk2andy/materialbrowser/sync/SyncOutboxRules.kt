package dev.sk2andy.materialbrowser.sync

object SyncOutboxRules {
    fun enqueue(
        pending: List<SyncPendingMutation>,
        mutation: SyncPendingMutation,
    ): List<SyncPendingMutation> {
        val replaceIndex = pending.indexOfLast { existing -> existing.isSupersededBy(mutation) }
        val retained = pending.filterNot { existing -> existing.isDiscardedBy(mutation) }
        if (replaceIndex < 0) return retained + mutation
        val insertionIndex = pending.take(replaceIndex).count { existing -> !existing.isDiscardedBy(mutation) }
        return retained.toMutableList().also { values ->
            values.add(insertionIndex.coerceAtMost(values.size), mutation)
        }
    }

    private fun SyncPendingMutation.isSupersededBy(next: SyncPendingMutation): Boolean = when {
        this is SyncPendingMutation.Navigate && next is SyncPendingMutation.Navigate ->
            sameTab(next.targetDeviceId, next.candyId)
        this is SyncPendingMutation.SetPinned && next is SyncPendingMutation.SetPinned ->
            sameTab(next.targetDeviceId, next.candyId)
        this is SyncPendingMutation.Reorder && next is SyncPendingMutation.Reorder ->
            targetDeviceId == next.targetDeviceId
        else -> false
    }

    private fun SyncPendingMutation.isDiscardedBy(next: SyncPendingMutation): Boolean =
        isSupersededBy(next) || next is SyncPendingMutation.Close && when (this) {
            is SyncPendingMutation.Navigate -> sameTab(next.targetDeviceId, next.candyId)
            is SyncPendingMutation.SetPinned -> sameTab(next.targetDeviceId, next.candyId)
            else -> false
        }

    private fun SyncPendingMutation.sameTab(target: String, candyId: String): Boolean =
        targetDeviceId == target && when (this) {
            is SyncPendingMutation.Navigate -> this.candyId == candyId
            is SyncPendingMutation.Close -> this.candyId == candyId
            is SyncPendingMutation.SetPinned -> this.candyId == candyId
            else -> false
        }
}
