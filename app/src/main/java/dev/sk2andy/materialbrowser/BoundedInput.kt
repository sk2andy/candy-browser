package dev.sk2andy.materialbrowser

import com.google.common.io.ByteStreams
import java.io.InputStream

internal fun InputStream.readAtMost(maxBytes: Int): ByteArray =
    ByteStreams.toByteArray(ByteStreams.limit(this, maxBytes.toLong()))
