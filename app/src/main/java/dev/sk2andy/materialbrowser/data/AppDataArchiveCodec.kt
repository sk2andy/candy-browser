package dev.sk2andy.materialbrowser.data

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONObject

internal data class AppDataArchiveEntry(
    val relativePath: String,
    val isDirectory: Boolean,
    val size: Long,
    val crc: Long,
)

internal data class AppDataArchiveInspection(
    val manifest: AppDataArchiveManifest,
    val entries: List<AppDataArchiveEntry>,
) {
    val archivedRootNames: Set<String>
        get() = entries.mapTo(mutableSetOf()) { entry -> entry.relativePath.substringBefore('/') }
}

internal data class AppDataArchiveExtraction(
    val manifest: AppDataArchiveManifest,
    val entries: List<AppDataArchiveEntry>,
)

internal enum class AppDataArchiveFailure {
    SourceNotDirectory,
    SourceSymbolicLink,
    SymbolicLink,
    ManifestNotFirst,
    ManifestTooLarge,
    InvalidManifest,
    UnsupportedFormatVersion,
    InvalidEntryPath,
    DuplicateEntry,
    EntryLimitExceeded,
    FileTooLarge,
    TotalSizeExceeded,
    TargetNotEmpty,
    TargetSymbolicLink,
    EmptyArchive,
    ArchiveIo,
}

internal class AppDataArchiveException(
    val failure: AppDataArchiveFailure,
    val entryName: String? = null,
    cause: Throwable? = null,
) : IOException(
    buildString {
        append(failure.name)
        entryName?.let { append(": ").append(it) }
    },
    cause,
)

internal object AppDataArchiveCodec {
    fun export(
        dataDirectory: Path,
        manifest: AppDataArchiveManifest,
        output: OutputStream,
    ) {
        ensureSupportedManifest(manifest)
        val sources = collectSources(dataDirectory)
        if (sources.none { source -> !source.isDirectory }) {
            throw AppDataArchiveException(AppDataArchiveFailure.EmptyArchive)
        }
        val zipOutput = ZipOutputStream(
            BufferedOutputStream(NonClosingOutputStream(output)),
        )
        try {
            writeManifest(zipOutput, manifest)
            var totalBytes = 0L
            sources.forEach { source ->
                ensureUnchangedSource(source)
                val entry = ZipEntry(source.entryName).apply {
                    time = manifest.exportedAtEpochMillis
                }
                zipOutput.putNextEntry(entry)
                if (!source.isDirectory) {
                    Files.newInputStream(source.path).use { input ->
                        val written = copyBounded(
                            input = input,
                            output = zipOutput,
                            maximumBytes = AppDataArchiveRules.MAX_FILE_BYTES,
                            tooLargeFailure = AppDataArchiveFailure.FileTooLarge,
                            entryName = source.entryName,
                        )
                        totalBytes = checkedTotal(totalBytes, written, source.entryName)
                    }
                }
                zipOutput.closeEntry()
            }
            zipOutput.finish()
            zipOutput.flush()
        } catch (failure: AppDataArchiveException) {
            throw failure
        } catch (failure: IOException) {
            throw AppDataArchiveException(AppDataArchiveFailure.ArchiveIo, cause = failure)
        } finally {
            runCatching(zipOutput::close)
        }
    }

    fun inspect(input: InputStream): AppDataArchiveInspection {
        val archive = readArchive(input, requireSupportedFormatBeforeEntries = false)
        return AppDataArchiveInspection(
            manifest = archive.manifest,
            entries = archive.entries,
        )
    }

    fun extract(input: InputStream, emptyTargetDirectory: Path): AppDataArchiveExtraction {
        val target = prepareEmptyTarget(emptyTargetDirectory)
        val archive = readArchive(
            input = input,
            requireSupportedFormatBeforeEntries = true,
            consumeEntry = { relativePath, isDirectory, entryInput ->
                val destination = safeDestination(target, relativePath)
                ensureNoSymbolicLinkInPath(target, destination)
                if (isDirectory) {
                    Files.createDirectories(destination)
                } else {
                    destination.parent?.let { parent -> Files.createDirectories(parent) }
                    Files.newOutputStream(destination).use { output ->
                        entryInput.copyTo(output)
                    }
                }
            },
        )
        return AppDataArchiveExtraction(
            manifest = archive.manifest,
            entries = archive.entries,
        )
    }

    private fun collectSources(dataDirectory: Path): List<SourceEntry> {
        if (Files.isSymbolicLink(dataDirectory)) {
            throw AppDataArchiveException(AppDataArchiveFailure.SourceSymbolicLink)
        }
        if (!Files.isDirectory(dataDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw AppDataArchiveException(AppDataArchiveFailure.SourceNotDirectory)
        }

        val sources = mutableListOf<SourceEntry>()
        var totalBytes = 0L
        try {
            Files.walkFileTree(
                dataDirectory,
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(
                        directory: Path,
                        attributes: BasicFileAttributes,
                    ): FileVisitResult {
                        if (directory == dataDirectory) return FileVisitResult.CONTINUE
                        val relativePath = dataDirectory.relativize(directory)
                        if (relativePath.nameCount == 1 &&
                            !AppDataArchiveRules.shouldExportTopLevel(relativePath.toString())
                        ) {
                            return FileVisitResult.SKIP_SUBTREE
                        }
                        rejectSymbolicLink(directory, attributes)
                        sources += SourceEntry(
                            path = directory,
                            entryName = archiveEntryName(relativePath, isDirectory = true),
                            isDirectory = true,
                            expectedSize = 0L,
                        )
                        ensureEntryCount(sources.size + 1)
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFile(
                        file: Path,
                        attributes: BasicFileAttributes,
                    ): FileVisitResult {
                        val relativePath = dataDirectory.relativize(file)
                        if (relativePath.nameCount == 1 &&
                            !AppDataArchiveRules.shouldExportTopLevel(relativePath.toString())
                        ) {
                            return FileVisitResult.CONTINUE
                        }
                        rejectSymbolicLink(file, attributes)
                        if (!attributes.isRegularFile) {
                            throw AppDataArchiveException(
                                AppDataArchiveFailure.InvalidEntryPath,
                                relativePath.toString(),
                            )
                        }
                        ensureFileSize(attributes.size(), relativePath.toString())
                        totalBytes = checkedTotal(totalBytes, attributes.size(), relativePath.toString())
                        sources += SourceEntry(
                            path = file,
                            entryName = archiveEntryName(relativePath, isDirectory = false),
                            isDirectory = false,
                            expectedSize = attributes.size(),
                        )
                        ensureEntryCount(sources.size + 1)
                        return FileVisitResult.CONTINUE
                    }
                },
            )
        } catch (failure: AppDataArchiveException) {
            throw failure
        } catch (failure: IOException) {
            throw AppDataArchiveException(AppDataArchiveFailure.ArchiveIo, cause = failure)
        }
        return sources.sortedBy(SourceEntry::entryName)
    }

    private fun writeManifest(output: ZipOutputStream, manifest: AppDataArchiveManifest) {
        val bytes = manifest.toJson().toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > AppDataArchiveRules.MAX_MANIFEST_BYTES) {
            throw AppDataArchiveException(AppDataArchiveFailure.ManifestTooLarge)
        }
        output.putNextEntry(ZipEntry(AppDataArchiveRules.MANIFEST_ENTRY_NAME).apply {
            time = manifest.exportedAtEpochMillis
        })
        output.write(bytes)
        output.closeEntry()
    }

    private fun readArchive(
        input: InputStream,
        requireSupportedFormatBeforeEntries: Boolean,
        consumeEntry: ((String, Boolean, InputStream) -> Unit)? = null,
    ): ReadArchiveResult {
        val zipInput = ZipInputStream(
            BufferedInputStream(NonClosingInputStream(input)),
        )
        try {
            val firstEntry = zipInput.nextEntry
                ?: throw AppDataArchiveException(AppDataArchiveFailure.ManifestNotFirst)
            if (firstEntry.name != AppDataArchiveRules.MANIFEST_ENTRY_NAME || firstEntry.isDirectory) {
                throw AppDataArchiveException(
                    AppDataArchiveFailure.ManifestNotFirst,
                    firstEntry.name,
                )
            }
            val manifestBytes = readBoundedBytes(
                input = zipInput,
                maximumBytes = AppDataArchiveRules.MAX_MANIFEST_BYTES.toLong(),
                tooLargeFailure = AppDataArchiveFailure.ManifestTooLarge,
                entryName = firstEntry.name,
            )
            zipInput.closeEntry()
            val manifest = try {
                parseManifest(manifestBytes)
            } catch (failure: RuntimeException) {
                throw AppDataArchiveException(AppDataArchiveFailure.InvalidManifest, cause = failure)
            }
            if (requireSupportedFormatBeforeEntries) ensureSupportedManifest(manifest)

            val seenPaths = mutableSetOf<String>()
            val filePaths = mutableSetOf<String>()
            val parentPaths = mutableSetOf<String>()
            val entries = mutableListOf<AppDataArchiveEntry>()
            var totalBytes = 0L
            var entryCount = 1
            while (true) {
                val zipEntry = zipInput.nextEntry ?: break
                entryCount += 1
                ensureEntryCount(entryCount)
                if (zipEntry.name == AppDataArchiveRules.MANIFEST_ENTRY_NAME) {
                    throw AppDataArchiveException(
                        AppDataArchiveFailure.DuplicateEntry,
                        zipEntry.name,
                    )
                }
                val relativePath = AppDataArchiveRules.dataRelativePath(
                    entryName = zipEntry.name,
                    isDirectory = zipEntry.isDirectory,
                ) ?: throw AppDataArchiveException(
                    AppDataArchiveFailure.InvalidEntryPath,
                    zipEntry.name,
                )
                validateUniqueHierarchy(
                    relativePath = relativePath,
                    isDirectory = zipEntry.isDirectory,
                    seenPaths = seenPaths,
                    filePaths = filePaths,
                    parentPaths = parentPaths,
                    entryName = zipEntry.name,
                )
                if (zipEntry.size >= 0L && AppDataArchiveRules.isFileSizeExceeded(zipEntry.size)) {
                    throw AppDataArchiveException(
                        AppDataArchiveFailure.FileTooLarge,
                        zipEntry.name,
                    )
                }
                if (zipEntry.size >= 0L &&
                    AppDataArchiveRules.isTotalSizeExceeded(totalBytes, zipEntry.size)
                ) {
                    throw AppDataArchiveException(
                        AppDataArchiveFailure.TotalSizeExceeded,
                        zipEntry.name,
                    )
                }

                val crc = CRC32()
                val remainingTotalBytes = AppDataArchiveRules.MAX_TOTAL_BYTES - totalBytes
                val entryByteLimit = if (zipEntry.isDirectory) {
                    0L
                } else {
                    minOf(AppDataArchiveRules.MAX_FILE_BYTES, remainingTotalBytes)
                }
                val countingInput = BoundedZipEntryInputStream(
                    input = zipInput,
                    maximumBytes = entryByteLimit,
                    limitFailure = if (remainingTotalBytes < AppDataArchiveRules.MAX_FILE_BYTES) {
                        AppDataArchiveFailure.TotalSizeExceeded
                    } else {
                        AppDataArchiveFailure.FileTooLarge
                    },
                    entryName = zipEntry.name,
                    crc = crc,
                )
                consumeEntry?.invoke(relativePath, zipEntry.isDirectory, countingInput)
                    ?: countingInput.copyTo(DiscardingOutputStream)
                countingInput.drain()
                val size = countingInput.byteCount
                zipInput.closeEntry()
                totalBytes = checkedTotal(totalBytes, size, zipEntry.name)
                entries += AppDataArchiveEntry(
                    relativePath = relativePath,
                    isDirectory = zipEntry.isDirectory,
                    size = size,
                    crc = crc.value,
                )
            }
            if (!requireSupportedFormatBeforeEntries) ensureSupportedManifest(manifest)
            if (entries.none { entry -> !entry.isDirectory }) {
                throw AppDataArchiveException(AppDataArchiveFailure.EmptyArchive)
            }
            return ReadArchiveResult(manifest, entries)
        } catch (failure: AppDataArchiveException) {
            throw failure
        } catch (failure: IOException) {
            throw AppDataArchiveException(AppDataArchiveFailure.ArchiveIo, cause = failure)
        } catch (failure: SecurityException) {
            throw AppDataArchiveException(AppDataArchiveFailure.ArchiveIo, cause = failure)
        } finally {
            runCatching(zipInput::close)
        }
    }

    private fun prepareEmptyTarget(target: Path): Path {
        if (Files.isSymbolicLink(target)) {
            throw AppDataArchiveException(AppDataArchiveFailure.TargetSymbolicLink)
        }
        try {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS) ||
                    Files.newDirectoryStream(target).use { entries -> entries.iterator().hasNext() }
                ) {
                    throw AppDataArchiveException(AppDataArchiveFailure.TargetNotEmpty)
                }
            } else {
                Files.createDirectories(target)
            }
            return target.toAbsolutePath().normalize()
        } catch (failure: AppDataArchiveException) {
            throw failure
        } catch (failure: IOException) {
            throw AppDataArchiveException(AppDataArchiveFailure.ArchiveIo, cause = failure)
        }
    }

    private fun safeDestination(target: Path, relativePath: String): Path {
        val destination = target.resolve(relativePath).normalize()
        if (!destination.startsWith(target)) {
            throw AppDataArchiveException(
                AppDataArchiveFailure.InvalidEntryPath,
                relativePath,
            )
        }
        return destination
    }

    private fun ensureNoSymbolicLinkInPath(target: Path, destination: Path) {
        var current = target
        target.relativize(destination).forEach { component ->
            current = current.resolve(component)
            if (Files.isSymbolicLink(current)) {
                throw AppDataArchiveException(
                    AppDataArchiveFailure.TargetSymbolicLink,
                    target.relativize(current).toString(),
                )
            }
        }
    }

    private fun validateUniqueHierarchy(
        relativePath: String,
        isDirectory: Boolean,
        seenPaths: MutableSet<String>,
        filePaths: MutableSet<String>,
        parentPaths: MutableSet<String>,
        entryName: String,
    ) {
        if (!seenPaths.add(relativePath)) {
            throw AppDataArchiveException(AppDataArchiveFailure.DuplicateEntry, entryName)
        }
        if (!isDirectory && relativePath in parentPaths) {
            throw AppDataArchiveException(AppDataArchiveFailure.InvalidEntryPath, entryName)
        }
        var parent = relativePath.substringBeforeLast('/', missingDelimiterValue = "")
        while (parent.isNotEmpty()) {
            if (parent in filePaths) {
                throw AppDataArchiveException(AppDataArchiveFailure.InvalidEntryPath, entryName)
            }
            parentPaths += parent
            parent = parent.substringBeforeLast('/', missingDelimiterValue = "")
        }
        if (!isDirectory) filePaths += relativePath
    }

    private fun ensureUnchangedSource(source: SourceEntry) {
        val attributes = try {
            Files.readAttributes(
                source.path,
                BasicFileAttributes::class.java,
                LinkOption.NOFOLLOW_LINKS,
            )
        } catch (failure: IOException) {
            throw AppDataArchiveException(
                AppDataArchiveFailure.ArchiveIo,
                source.entryName,
                failure,
            )
        }
        rejectSymbolicLink(source.path, attributes)
        if (source.isDirectory != attributes.isDirectory ||
            (!source.isDirectory && (!attributes.isRegularFile || attributes.size() != source.expectedSize))
        ) {
            throw AppDataArchiveException(AppDataArchiveFailure.ArchiveIo, source.entryName)
        }
    }

    private fun rejectSymbolicLink(path: Path, attributes: BasicFileAttributes) {
        if (attributes.isSymbolicLink || Files.isSymbolicLink(path)) {
            throw AppDataArchiveException(
                AppDataArchiveFailure.SymbolicLink,
                path.toString(),
            )
        }
    }

    private fun archiveEntryName(relativePath: Path, isDirectory: Boolean): String {
        val relative = relativePath.joinToString("/") { component -> component.toString() }
        val name = AppDataArchiveRules.DATA_ENTRY_PREFIX + relative + if (isDirectory) "/" else ""
        if (AppDataArchiveRules.dataRelativePath(name, isDirectory) == null) {
            throw AppDataArchiveException(AppDataArchiveFailure.InvalidEntryPath, name)
        }
        return name
    }

    private fun ensureEntryCount(entryCount: Int) {
        if (AppDataArchiveRules.isEntryLimitExceeded(entryCount)) {
            throw AppDataArchiveException(AppDataArchiveFailure.EntryLimitExceeded)
        }
    }

    private fun ensureFileSize(size: Long, entryName: String) {
        if (AppDataArchiveRules.isFileSizeExceeded(size)) {
            throw AppDataArchiveException(AppDataArchiveFailure.FileTooLarge, entryName)
        }
    }

    private fun checkedTotal(current: Long, added: Long, entryName: String): Long {
        if (AppDataArchiveRules.isTotalSizeExceeded(current, added)) {
            throw AppDataArchiveException(AppDataArchiveFailure.TotalSizeExceeded, entryName)
        }
        return current + added
    }

    private fun copyBounded(
        input: InputStream,
        output: OutputStream,
        maximumBytes: Long,
        tooLargeFailure: AppDataArchiveFailure,
        entryName: String,
    ): Long {
        var total = 0L
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return total
            if (read == 0) continue
            if (total > maximumBytes - read) {
                throw AppDataArchiveException(tooLargeFailure, entryName)
            }
            output.write(buffer, 0, read)
            total += read
        }
    }

    private fun readBoundedBytes(
        input: InputStream,
        maximumBytes: Long,
        tooLargeFailure: AppDataArchiveFailure,
        entryName: String,
    ): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        copyBounded(input, output, maximumBytes, tooLargeFailure, entryName)
        return output.toByteArray()
    }

    private fun AppDataArchiveManifest.toJson(): String = JSONObject()
        .put("formatVersion", formatVersion)
        .put("packageName", packageName)
        .put("appVersionName", appVersionName)
        .put("appVersionCode", appVersionCode)
        .put("webViewVersion", webViewVersion ?: JSONObject.NULL)
        .put("sdkInt", sdkInt)
        .put("exportedAtEpochMillis", exportedAtEpochMillis)
        .toString()

    private fun parseManifest(bytes: ByteArray): AppDataArchiveManifest {
        val text = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
        val json = JSONObject(text)
        return AppDataArchiveManifest(
            formatVersion = json.requiredLong("formatVersion").toIntExact(),
            packageName = json.requiredNonBlankString("packageName"),
            appVersionName = json.requiredNonBlankString("appVersionName"),
            appVersionCode = json.requiredLong("appVersionCode").also { require(it >= 0L) },
            webViewVersion = if (json.isNull("webViewVersion")) {
                null.also { require(json.has("webViewVersion")) }
            } else {
                json.requiredNonBlankString("webViewVersion")
            },
            sdkInt = json.requiredLong("sdkInt").toIntExact().also { require(it >= 0) },
            exportedAtEpochMillis = json.requiredLong("exportedAtEpochMillis")
                .also { require(it >= 0L) },
        )
    }

    private fun JSONObject.requiredNonBlankString(name: String): String =
        get(name).let { value ->
            require(value is String && value.isNotBlank() && value.none(Char::isISOControl))
            value
        }

    private fun JSONObject.requiredLong(name: String): Long =
        get(name).let { value ->
            require(value is Number)
            value.toString().toLongOrNull() ?: error("$name is not an integer")
        }

    private fun Long.toIntExact(): Int {
        require(this in Int.MIN_VALUE..Int.MAX_VALUE)
        return toInt()
    }

    private fun ensureSupportedManifest(manifest: AppDataArchiveManifest) {
        if (manifest.formatVersion != AppDataArchiveRules.FORMAT_VERSION) {
            throw AppDataArchiveException(AppDataArchiveFailure.UnsupportedFormatVersion)
        }
        if (manifest.packageName.isBlank() ||
            manifest.packageName.any(Char::isISOControl) ||
            manifest.appVersionName.isBlank() ||
            manifest.appVersionName.any(Char::isISOControl) ||
            manifest.appVersionCode < 0L ||
            manifest.webViewVersion?.isBlank() == true ||
            manifest.webViewVersion?.any(Char::isISOControl) == true ||
            manifest.sdkInt < 0 ||
            manifest.exportedAtEpochMillis < 0L
        ) {
            throw AppDataArchiveException(AppDataArchiveFailure.InvalidManifest)
        }
    }

    private data class SourceEntry(
        val path: Path,
        val entryName: String,
        val isDirectory: Boolean,
        val expectedSize: Long,
    )

    private data class ReadArchiveResult(
        val manifest: AppDataArchiveManifest,
        val entries: List<AppDataArchiveEntry>,
    )

    private class BoundedZipEntryInputStream(
        private val input: InputStream,
        private val maximumBytes: Long,
        private val limitFailure: AppDataArchiveFailure,
        private val entryName: String,
        private val crc: CRC32,
    ) : InputStream() {
        var byteCount: Long = 0L
            private set

        override fun read(): Int {
            val value = input.read()
            if (value >= 0) {
                addBytes(1)
                crc.update(value)
            }
            return value
        }

        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            val read = input.read(bytes, offset, length)
            if (read > 0) {
                addBytes(read)
                crc.update(bytes, offset, read)
            }
            return read
        }

        fun drain() {
            copyTo(DiscardingOutputStream)
        }

        private fun addBytes(count: Int) {
            if (byteCount > maximumBytes - count) {
                throw AppDataArchiveException(
                    limitFailure,
                    entryName,
                )
            }
            byteCount += count
        }
    }

    private object DiscardingOutputStream : OutputStream() {
        override fun write(value: Int) = Unit

        override fun write(bytes: ByteArray, offset: Int, length: Int) = Unit
    }

    private class NonClosingInputStream(input: InputStream) : FilterInputStream(input) {
        override fun close() = Unit
    }

    private class NonClosingOutputStream(output: OutputStream) : FilterOutputStream(output) {
        override fun close() = flush()
    }

    private const val COPY_BUFFER_BYTES = 16 * 1024
}
