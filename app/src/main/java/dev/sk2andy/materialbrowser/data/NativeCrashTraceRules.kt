package dev.sk2andy.materialbrowser.data

/** Reads only symbolization metadata from Android's binary debuggerd Tombstone protobuf. */
internal object NativeCrashTraceRules {
    const val MAX_INPUT_BYTES = 2 * 1024 * 1024
    private const val MAX_TRACE_BYTES = AppLogRules.MAX_RECORD_BYTES - 512
    private const val MAX_FRAMES = 64
    private const val MAX_PATH_BYTES = 4_096
    private const val MAX_SYMBOL_BYTES = 256
    private const val MAX_BUILD_ID_BYTES = 128
    private val MODULE_NAME = Regex(
        "[A-Za-z0-9_][A-Za-z0-9_.+-]{0,127}\\.(so|apk|oat|odex|dex|art)(\\.[0-9]+)*",
    )
    private val SYMBOL_NAME = Regex("[A-Za-z_$~][A-Za-z0-9_$:.<>~]{0,255}")
    private val BUILD_ID = Regex("[0-9a-fA-F]{8,128}")

    fun parse(bytes: ByteArray): String? {
        if (bytes.isEmpty() || bytes.size > MAX_INPUT_BYTES) return null
        return try {
            parseTrace(bytes)
        } catch (_: MalformedTraceException) {
            null
        }
    }

    private fun parseTrace(bytes: ByteArray): String? {
        var architecture = 0L
        var crashedThreadId = 0L
        var signal = Signal()
        val header = ProtoReader(bytes)
        header.readFields { field ->
            when (field.number) {
                1 -> architecture = header.number(field)
                6 -> crashedThreadId = header.number(field)
                10 -> signal = parseSignal(header.message(field))
                else -> header.skip(field)
            }
        }
        val architectureName = when (architecture) {
            0L -> "arm32"
            1L -> "arm64"
            2L -> "x86"
            3L -> "x86_64"
            4L -> "riscv64"
            else -> return null
        }
        if (crashedThreadId !in 1L..Int.MAX_VALUE.toLong()) return null
        var frames = emptyList<Frame>()
        val threads = ProtoReader(bytes)
        threads.readFields { field ->
            if (field.number == 16) {
                parseThreadEntry(threads.message(field), crashedThreadId)?.let { frames = it }
            } else {
                threads.skip(field)
            }
        }
        if (frames.isEmpty()) return null
        val output = StringBuilder(
            "native crash arch=$architectureName signal=${signal.number} code=${signal.code}\n",
        )
        var renderedFrames = 0
        frames.forEachIndexed { index, frame ->
            val module = moduleName(frame.fileName) ?: return@forEachIndexed
            val line = buildString {
                append("  #")
                append(index.toString().padStart(2, '0'))
                append(" pc ")
                append(java.lang.Long.toUnsignedString(frame.relativePc, 16).padStart(16, '0'))
                append(' ')
                append(module)
                frame.functionName?.takeIf(SYMBOL_NAME::matches)?.let { symbol ->
                    append(" (")
                    append(symbol)
                    append('+')
                    append(java.lang.Long.toUnsignedString(frame.functionOffset))
                    append(')')
                }
                frame.buildId?.takeIf(BUILD_ID::matches)?.let { buildId ->
                    append(" (BuildId: ")
                    append(buildId.lowercase())
                    append(')')
                }
                append('\n')
            }
            if (output.length + line.length > MAX_TRACE_BYTES) {
                return output.toString().takeIf { renderedFrames > 0 }
            }
            output.append(line)
            renderedFrames++
        }
        return output.toString().takeIf { renderedFrames > 0 }
    }

    private fun parseSignal(reader: ProtoReader): Signal {
        var number = 0
        var code = 0
        reader.readFields { field ->
            when (field.number) {
                1 -> number = reader.number(field).toInt()
                3 -> code = reader.number(field).toInt()
                else -> reader.skip(field)
            }
        }
        return Signal(number, code)
    }

    private fun parseThreadEntry(reader: ProtoReader, crashedThreadId: Long): List<Frame>? {
        var key = 0L
        var thread: ProtoReader? = null
        reader.readFields { field ->
            when (field.number) {
                1 -> key = reader.number(field)
                2 -> thread = reader.message(field)
                else -> reader.skip(field)
            }
        }
        if (key != crashedThreadId) return null
        val threadReader = thread ?: return null
        var id = 0L
        val frames = mutableListOf<Frame>()
        threadReader.readFields { field ->
            when (field.number) {
                1 -> id = threadReader.number(field)
                4 -> {
                    val frame = threadReader.message(field)
                    if (frames.size < MAX_FRAMES) frames += parseFrame(frame)
                }
                else -> threadReader.skip(field)
            }
        }
        return frames.takeIf { id == crashedThreadId }
    }

    private fun parseFrame(reader: ProtoReader): Frame {
        var relativePc = 0L
        var functionName: String? = null
        var functionOffset = 0L
        var fileName: String? = null
        var buildId: String? = null
        reader.readFields { field ->
            when (field.number) {
                1 -> relativePc = reader.number(field)
                4 -> functionName = reader.text(field, MAX_SYMBOL_BYTES)
                5 -> functionOffset = reader.number(field)
                6 -> fileName = reader.text(field, MAX_PATH_BYTES)
                8 -> buildId = reader.text(field, MAX_BUILD_ID_BYTES)
                else -> reader.skip(field)
            }
        }
        return Frame(relativePc, functionName, functionOffset, fileName, buildId)
    }

    private fun moduleName(path: String?): String? {
        if (path == null || path.contains("://") || path.any { it < ' ' || it == '\u007f' }) {
            return null
        }
        if (path.any { it == '?' || it == '#' || it == '\\' }) return null
        return path.substringAfterLast('/').takeIf(MODULE_NAME::matches)
    }

    private data class Signal(val number: Int = 0, val code: Int = 0)

    private data class Frame(
        val relativePc: Long,
        val functionName: String?,
        val functionOffset: Long,
        val fileName: String?,
        val buildId: String?,
    )

    private data class Field(val number: Int, val wireType: Int)

    private class MalformedTraceException : RuntimeException()

    private class ProtoReader(
        private val bytes: ByteArray,
        private var position: Int = 0,
        private val end: Int = bytes.size,
    ) {
        fun readFields(read: (Field) -> Unit) {
            while (position < end) {
                val tag = varint()
                if (tag !in 1L..0xffff_ffffL) throw MalformedTraceException()
                val number = (tag ushr 3).toInt()
                val wireType = (tag and 7L).toInt()
                if (number == 0 || (wireType != 0 && wireType != 1 && wireType != 2 && wireType != 5)) {
                    throw MalformedTraceException()
                }
                read(Field(number, wireType))
            }
        }

        fun number(field: Field): Long {
            if (field.wireType != 0) throw MalformedTraceException()
            return varint()
        }

        fun message(field: Field): ProtoReader {
            if (field.wireType != 2) throw MalformedTraceException()
            val length = length()
            val message = ProtoReader(bytes, position, position + length)
            position += length
            return message
        }

        fun text(field: Field, maxBytes: Int): String? {
            if (field.wireType != 2) throw MalformedTraceException()
            val length = length()
            val text = if (length <= maxBytes) {
                String(bytes, position, length, Charsets.UTF_8)
            } else {
                null
            }
            position += length
            return text
        }

        fun skip(field: Field) {
            when (field.wireType) {
                0 -> varint()
                1 -> advance(8)
                2 -> advance(length())
                5 -> advance(4)
                else -> throw MalformedTraceException()
            }
        }

        private fun advance(length: Int) {
            if (length > end - position) throw MalformedTraceException()
            position += length
        }

        private fun length(): Int {
            val length = varint()
            if (length < 0L || length > (end - position).toLong()) {
                throw MalformedTraceException()
            }
            return length.toInt()
        }

        private fun varint(): Long {
            var value = 0L
            repeat(10) { index ->
                if (position >= end) throw MalformedTraceException()
                val byte = bytes[position++].toInt() and 0xff
                if (index == 9 && byte > 1) throw MalformedTraceException()
                value = value or ((byte and 0x7f).toLong() shl (index * 7))
                if ((byte and 0x80) == 0) return value
            }
            throw MalformedTraceException()
        }
    }
}
