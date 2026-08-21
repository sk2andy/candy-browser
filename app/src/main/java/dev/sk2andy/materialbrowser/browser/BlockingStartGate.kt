package dev.sk2andy.materialbrowser.browser

internal class BlockingStartGate<T> {
    private val pending = linkedMapOf<String, T>()

    var isReady: Boolean = false
        private set

    fun enqueue(key: String, value: T) {
        check(!isReady) { "Ready gate cannot queue starts" }
        pending[key] = value
    }

    fun cancel(key: String) {
        pending.remove(key)
    }

    fun cancelAll() {
        pending.clear()
    }

    fun markReady(): Map<String, T> {
        if (isReady) return emptyMap()
        isReady = true
        return pending.toMap().also { pending.clear() }
    }
}
