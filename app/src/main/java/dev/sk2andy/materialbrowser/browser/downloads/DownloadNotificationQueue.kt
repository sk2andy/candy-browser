package dev.sk2andy.materialbrowser.browser.downloads

/** Coalesces updates across transfers and leaves room for other app notifications. */
internal class DownloadNotificationQueue<T>(
    private val lock: Any,
    private val now: () -> Long,
    private val schedule: (Long, () -> Unit) -> Unit,
    private val send: (T) -> Unit,
) {
    private data class Pending<T>(val value: T, val priority: Boolean)

    private val pending = linkedMapOf<Int, Pending<T>>()
    private var lastSent: Long? = null
    private var scheduled = false

    fun enqueue(id: Int, value: T, priority: Boolean) = synchronized(lock) {
        pending[id] = Pending(value, priority || pending[id]?.priority == true)
        drain()
    }

    fun remove(id: Int) {
        synchronized(lock) { pending.remove(id) }
    }

    fun removeIf(predicate: (Int, T) -> Boolean) {
        synchronized(lock) { pending.entries.removeAll { (id, update) -> predicate(id, update.value) } }
    }

    private fun drain() {
        if (scheduled || pending.isEmpty()) return
        val delay = lastSent?.let { (it + UPDATE_INTERVAL_MS - now()).coerceAtLeast(0L) } ?: 0L
        if (delay > 0L) {
            scheduled = true
            schedule(delay) {
                synchronized(lock) {
                    scheduled = false
                    drain()
                }
            }
            return
        }
        val entry = pending.entries.firstOrNull { it.value.priority } ?: pending.entries.first()
        val update = entry.value
        pending.remove(entry.key)
        send(update.value)
        lastSent = now()
        drain()
    }

    private companion object {
        const val UPDATE_INTERVAL_MS = 500L
    }
}
