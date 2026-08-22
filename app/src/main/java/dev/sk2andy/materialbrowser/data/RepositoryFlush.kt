package dev.sk2andy.materialbrowser.data

import java.util.concurrent.ExecutorService

internal fun ExecutorService.awaitIdle(): Boolean = runCatching {
    submit {}.get()
}.isSuccess
