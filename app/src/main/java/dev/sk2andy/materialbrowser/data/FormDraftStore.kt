package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.util.AtomicFile
import dev.sk2andy.materialbrowser.browser.formdraft.FormDraft
import dev.sk2andy.materialbrowser.browser.formdraft.FormDraftCodec
import dev.sk2andy.materialbrowser.browser.formdraft.FormDraftRules
import dev.sk2andy.materialbrowser.data.sync.AndroidSyncSecretProtector
import java.io.File

/** Call on a serialized worker. Draft content never enters portable app data or logs. */
internal class FormDraftStore(context: Context) {
    private val settings = BrowserSessionStore(context.applicationContext)
    private val directory = File(context.applicationContext.noBackupFilesDir, "form_drafts")
    private val file = AtomicFile(File(directory, "drafts.enc"))
    private val protector = AndroidSyncSecretProtector("candy_form_drafts_v1")

    fun load(profileId: String, url: String, now: Long): FormDraft? = synchronized(LOCK) {
        val stored = read()
        val live = FormDraftRules.prune(stored, now)
        if (live != stored) write(live)
        live.firstOrNull { it.profileId == profileId && it.url == url }
    }

    fun save(draft: FormDraft, isPrivate: Boolean, now: Long, expectedEpoch: Long = epoch): Boolean = synchronized(LOCK) {
        if (isPrivate || !settings.loadFormDraftSavingEnabled() || expectedEpoch != epoch) return false
        val valid = FormDraftRules.sanitize(draft, now) ?: return false
        write(FormDraftRules.prune(read().filterNot {
            it.profileId == valid.profileId && it.url == valid.url
        } + valid, now))
    }

    fun delete(profileId: String, url: String, now: Long): Boolean = synchronized(LOCK) {
        write(FormDraftRules.prune(read().filterNot { it.profileId == profileId && it.url == url }, now))
    }

    fun deleteProfile(profileId: String, now: Long): Boolean = synchronized(LOCK) {
        write(FormDraftRules.prune(read().filterNot { it.profileId == profileId }, now))
    }

    fun clear(): Boolean = synchronized(LOCK) {
        epoch++
        deleteFiles()
    }

    private fun deleteFiles(): Boolean {
        file.delete()
        return !file.baseFile.exists() && !File(directory, "drafts.enc.bak").exists()
    }

    private fun read(): List<FormDraft> = runCatching {
        if (!file.baseFile.exists() && !File(directory, "drafts.enc.bak").exists()) return emptyList()
        val encrypted = file.openRead().use { input ->
            val bytes = input.readNBytes(MAX_ENCRYPTED_BYTES + 1)
            require(bytes.size <= MAX_ENCRYPTED_BYTES)
            bytes
        }
        val plaintext = protector.decrypt(encrypted, AAD)
        try {
            require(plaintext.size <= MAX_PLAINTEXT_BYTES)
            FormDraftCodec.decode(plaintext)
        } finally {
            plaintext.fill(0)
        }
    }.getOrElse { emptyList() }

    private fun write(drafts: List<FormDraft>): Boolean {
        if (drafts.isEmpty()) return deleteFiles()
        val plaintext = FormDraftCodec.encode(drafts)
        return try {
            require(plaintext.size <= MAX_PLAINTEXT_BYTES)
            val encrypted = protector.encrypt(plaintext, AAD)
            if (!directory.exists() && !directory.mkdirs()) return false
            val output = file.startWrite()
            try {
                output.write(encrypted)
                file.finishWrite(output)
                true
            } catch (failure: Exception) {
                file.failWrite(output)
                false
            }
        } catch (failure: Exception) {
            false
        } finally {
            plaintext.fill(0)
        }
    }

    companion object {
        @Volatile internal var epoch = 0L
            private set
        private val LOCK = Any()
        private val AAD = "candy/form-drafts/v1".toByteArray(Charsets.UTF_8)
        private const val MAX_PLAINTEXT_BYTES = 6 * 1_024 * 1_024
        private const val MAX_ENCRYPTED_BYTES = 8 * 1_024 * 1_024
    }
}
