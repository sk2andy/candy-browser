package dev.sk2andy.materialbrowser.data

data class FavoriteMutation(
    val before: List<FavoriteEntry>,
    val applied: List<FavoriteEntry>,
    val added: Boolean,
    val revision: Long,
)

internal object FavoriteUndoRules {
    fun restore(
        current: List<FavoriteEntry>,
        currentRevision: Long,
        mutation: FavoriteMutation,
    ): List<FavoriteEntry>? {
        if (currentRevision != mutation.revision) return null
        if (current != mutation.applied) return null
        return mutation.before
    }
}
