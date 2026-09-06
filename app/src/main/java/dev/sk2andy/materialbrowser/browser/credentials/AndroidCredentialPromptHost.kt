package dev.sk2andy.materialbrowser.browser.credentials

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.CancellationSignal
import androidx.appcompat.app.AlertDialog
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CreateCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import java.util.concurrent.atomic.AtomicBoolean

/** Android system-UI implementation. It intentionally owns no credential database. */
internal class AndroidCredentialPromptHost private constructor(
    private val activity: Activity,
    private val credentialManager: CredentialManager,
) : CredentialPromptHost {
    private var pending: PendingCompletion<*>? = null
    private var closed = false

    override fun saveLogin(
        prompt: CredentialLoginSavePrompt,
        onComplete: (Boolean) -> Unit,
    ) {
        val completion = begin(fallback = false, onComplete = onComplete) ?: return
        val cancellationSignal = CancellationSignal().also(completion::attach)
        val request = runCatching {
            CreatePasswordRequest(
                id = prompt.login.username,
                password = prompt.login.password,
                origin = prompt.identity.origin,
                isAutoSelectAllowed = false,
            )
        }.getOrElse {
            completion.complete(false)
            return
        }
        credentialManager.createCredentialAsync(
            context = activity,
            request = request,
            cancellationSignal = cancellationSignal,
            executor = activity.mainExecutor,
            callback = object : CredentialManagerCallback<
                CreateCredentialResponse,
                CreateCredentialException,
                > {
                override fun onResult(result: CreateCredentialResponse) = completion.complete(true)

                override fun onError(e: CreateCredentialException) = completion.complete(false)
            },
        )
    }

    override fun selectLogin(
        prompt: CredentialLoginSelectPrompt,
        onComplete: (CredentialLogin?) -> Unit,
    ) {
        val completion = begin<CredentialLogin?>(fallback = null, onComplete = onComplete) ?: return
        val cancellationSignal = CancellationSignal().also(completion::attach)
        val request = runCatching {
            GetCredentialRequest.Builder()
                .addCredentialOption(GetPasswordOption(prompt.allowedUserIds))
                .setOrigin(prompt.identity.origin)
                .build()
        }.getOrElse {
            completion.complete(null)
            return
        }
        credentialManager.getCredentialAsync(
            context = activity,
            request = request,
            cancellationSignal = cancellationSignal,
            executor = activity.mainExecutor,
            callback = object : CredentialManagerCallback<
                GetCredentialResponse,
                GetCredentialException,
                > {
                override fun onResult(result: GetCredentialResponse) {
                    val credential = result.credential as? PasswordCredential
                    completion.complete(
                        credential?.let { value ->
                            CredentialPromptRules.login(
                                username = value.id,
                                password = value.password,
                            )
                        },
                    )
                }

                override fun onError(e: GetCredentialException) = completion.complete(null)
            },
        )
    }

    override fun selectIdentityProvider(
        prompt: IdentityCredentialProviderPrompt,
        onComplete: (Int?) -> Unit,
    ) = showChoice(
        title = prompt.identity.origin,
        labels = prompt.providers.map { provider -> "${provider.name} (${provider.domain})" },
        ids = prompt.providers.map(IdentityCredentialProvider::id),
        onComplete = onComplete,
    )

    override fun selectIdentityAccount(
        prompt: IdentityCredentialAccountPrompt,
        onComplete: (Int?) -> Unit,
    ) = showChoice(
        title = "${prompt.providerName} (${prompt.providerDomain})",
        labels = prompt.accounts.map { account -> "${account.name}\n${account.email}" },
        ids = prompt.accounts.map(IdentityCredentialAccount::id),
        onComplete = onComplete,
    )

    override fun confirmIdentityPrivacyPolicy(
        prompt: IdentityCredentialPrivacyPrompt,
        onComplete: (Boolean) -> Unit,
    ) {
        val completion = begin(fallback = false, onComplete = onComplete) ?: return
        val message = buildString {
            append(prompt.providerDomain)
            append("\n\n")
            append(prompt.privacyPolicyUrl)
            prompt.termsOfServiceUrl?.let { termsUrl ->
                append("\n\n")
                append(termsUrl)
            }
        }
        runCatching {
            AlertDialog.Builder(activity)
                .setTitle(prompt.providerName)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ -> completion.complete(true) }
                .setNegativeButton(android.R.string.cancel) { _, _ -> completion.complete(false) }
                .setOnCancelListener { completion.complete(false) }
                .create()
                .also(completion::attach)
                .show()
        }.onFailure { completion.complete(false) }
    }

    override fun close() {
        if (closed) return
        closed = true
        pending?.cancel()
        pending = null
    }

    private fun showChoice(
        title: String,
        labels: List<String>,
        ids: List<Int>,
        onComplete: (Int?) -> Unit,
    ) {
        if (labels.isEmpty() || labels.size != ids.size) {
            onComplete(null)
            return
        }
        val completion = begin<Int?>(fallback = null, onComplete = onComplete) ?: return
        runCatching {
            AlertDialog.Builder(activity)
                .setTitle(title)
                .setItems(labels.toTypedArray()) { _, index -> completion.complete(ids[index]) }
                .setNegativeButton(android.R.string.cancel) { _, _ -> completion.complete(null) }
                .setOnCancelListener { completion.complete(null) }
                .create()
                .also(completion::attach)
                .show()
        }.onFailure { completion.complete(null) }
    }

    private fun <T> begin(fallback: T, onComplete: (T) -> Unit): PendingCompletion<T>? {
        if (closed || activity.isFinishing || activity.isDestroyed) {
            onComplete(fallback)
            return null
        }
        pending?.cancel()
        return PendingCompletion(
            fallback = fallback,
            onComplete = onComplete,
            onFinished = { finished ->
                if (pending === finished) pending = null
            },
        ).also { pending = it }
    }

    private class PendingCompletion<T>(
        private val fallback: T,
        private val onComplete: (T) -> Unit,
        private val onFinished: (PendingCompletion<T>) -> Unit,
    ) {
        private val completed = AtomicBoolean(false)
        private var cancellationSignal: CancellationSignal? = null
        private var dialog: AlertDialog? = null

        fun attach(signal: CancellationSignal) {
            if (completed.get()) signal.cancel() else cancellationSignal = signal
        }

        fun attach(dialog: AlertDialog) {
            if (completed.get()) dialog.dismiss() else this.dialog = dialog
        }

        fun complete(value: T) {
            if (!completed.compareAndSet(false, true)) return
            cancellationSignal = null
            dialog = null
            onFinished(this)
            onComplete(value)
        }

        fun cancel() {
            if (completed.get()) return
            cancellationSignal?.cancel()
            dialog?.dismiss()
            complete(fallback)
        }
    }

    companion object {
        fun create(context: Context): AndroidCredentialPromptHost? {
            val activity = activityContext(context) ?: return null
            return AndroidCredentialPromptHost(
                activity = activity,
                credentialManager = CredentialManager.create(activity),
            )
        }

        fun activityContext(context: Context): Activity? = context.findActivity()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
