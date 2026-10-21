package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.util.AtomicFile
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowConfiguration
import java.io.File
import java.io.IOException

internal class CustomWorkflowStore internal constructor(file: File) {
    constructor(context: Context) : this(File(context.applicationContext.filesDir, FILE_NAME))

    private val file = AtomicFile(file)

    fun load(): CustomWorkflowConfiguration = synchronized(lock) {
        val bytes = try {
            file.openRead().use { input -> input.readNBytes(CustomWorkflowCodec.MAX_JSON_BYTES + 1) }
        } catch (_: IOException) {
            return@synchronized CustomWorkflowConfiguration()
        }
        CustomWorkflowCodec.decode(bytes)
    }

    fun save(configuration: CustomWorkflowConfiguration): Boolean = synchronized(lock) {
        val bytes = CustomWorkflowCodec.encode(configuration) ?: return@synchronized false
        try {
            val stream = file.startWrite()
            try {
                stream.write(bytes)
                file.finishWrite(stream)
            } catch (error: Throwable) {
                file.failWrite(stream)
                throw error
            }
            true
        } catch (_: IOException) {
            false
        }
    }

    companion object {
        const val FILE_NAME = CustomWorkflowCodec.FILE_NAME
        private val lock = Any()
    }
}
