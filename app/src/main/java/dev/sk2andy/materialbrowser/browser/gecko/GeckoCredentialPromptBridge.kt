package dev.sk2andy.materialbrowser.browser.gecko

import dev.sk2andy.materialbrowser.browser.credentials.CredentialLoginSavePrompt
import dev.sk2andy.materialbrowser.browser.credentials.CredentialLoginSelectPrompt
import dev.sk2andy.materialbrowser.browser.credentials.CredentialPromptHost
import dev.sk2andy.materialbrowser.browser.credentials.CredentialPromptIdentity
import dev.sk2andy.materialbrowser.browser.credentials.CredentialPromptRules
import dev.sk2andy.materialbrowser.browser.credentials.IdentityCredentialAccount
import dev.sk2andy.materialbrowser.browser.credentials.IdentityCredentialAccountPrompt
import dev.sk2andy.materialbrowser.browser.credentials.IdentityCredentialProvider
import dev.sk2andy.materialbrowser.browser.credentials.IdentityCredentialProviderPrompt
import java.util.concurrent.atomic.AtomicBoolean
import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

/** Exact GeckoView-140 prompt translation; credential policy stays engine-neutral. */
internal class GeckoCredentialPromptBridge(
    private val currentLoginSelectionIdentity: () -> CredentialPromptIdentity?,
    private val currentSecureIdentity: () -> CredentialPromptIdentity?,
    private val currentHost: () -> CredentialPromptHost?,
) {
    fun onLoginSave(
        request: GeckoSession.PromptDelegate.AutocompleteRequest<Autocomplete.LoginSaveOption>,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val identity = currentSecureIdentity() ?: return dismissed(request)
        val option = request.options.singleOrNull() ?: return dismissed(request)
        if (!CredentialPromptRules.matchesHttpsOrigin(option.value.origin, identity.origin)) {
            return dismissed(request)
        }
        val login = CredentialPromptRules.login(option.value.username, option.value.password)
            ?: return dismissed(request)
        val host = currentHost() ?: return dismissed(request)
        return pending(request) { complete ->
            host.saveLogin(CredentialLoginSavePrompt(identity, login)) { accepted ->
                complete(
                    Unit.takeIf {
                        accepted && CredentialPromptRules.remainsCurrent(
                            identity,
                            currentSecureIdentity(),
                        )
                    },
                ) { request.confirm(option) }
            }
        }
    }

    fun onLoginSelect(
        request: GeckoSession.PromptDelegate.AutocompleteRequest<Autocomplete.LoginSelectOption>,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val identity = currentLoginSelectionIdentity() ?: return dismissed(request)
        val allowedUserIds = CredentialPromptRules.allowedUserIds(
            request.options.map { option -> option.value.username },
        ) ?: return dismissed(request)
        val host = currentHost() ?: return dismissed(request)
        return pending(request) { complete ->
            host.selectLogin(CredentialLoginSelectPrompt(identity, allowedUserIds)) { login ->
                val accepted = login?.takeIf { candidate ->
                    CredentialPromptRules.acceptsSelectedUser(candidate.username, allowedUserIds) &&
                        CredentialPromptRules.remainsCurrent(
                            identity,
                            currentLoginSelectionIdentity(),
                        )
                }
                complete(accepted) { selected ->
                    request.confirm(
                        Autocomplete.LoginSelectOption(
                            Autocomplete.LoginEntry.Builder()
                                .origin(identity.origin)
                                .formActionOrigin(identity.origin)
                                .username(selected.username)
                                .password(selected.password)
                                .build(),
                        ),
                    )
                }
            }
        }
    }

    fun onIdentityProviderSelect(
        prompt: GeckoSession.PromptDelegate.IdentityCredential.ProviderSelectorPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val identity = currentSecureIdentity() ?: return dismissed(prompt)
        val providers = CredentialPromptRules.providers(
            prompt.providers.map { provider ->
                IdentityCredentialProvider(
                    id = provider.id,
                    name = provider.name,
                    domain = provider.domain,
                )
            },
        ) ?: return dismissed(prompt)
        val host = currentHost() ?: return dismissed(prompt)
        return pending(prompt) { complete ->
            host.selectIdentityProvider(IdentityCredentialProviderPrompt(identity, providers)) { id ->
                val selected = id?.takeIf { candidate ->
                    providers.any { provider -> provider.id == candidate } &&
                        CredentialPromptRules.remainsCurrent(identity, currentSecureIdentity())
                }
                complete(selected, prompt::confirm)
            }
        }
    }

    fun onIdentityAccountSelect(
        prompt: GeckoSession.PromptDelegate.IdentityCredential.AccountSelectorPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val identity = currentSecureIdentity() ?: return dismissed(prompt)
        val providerName = CredentialPromptRules.displayLabel(prompt.provider.name)
            ?: return dismissed(prompt)
        val providerDomain = CredentialPromptRules.providerDomain(prompt.provider.domain)
            ?: return dismissed(prompt)
        val accounts = CredentialPromptRules.accounts(
            prompt.accounts.map { account ->
                IdentityCredentialAccount(
                    id = account.id,
                    name = account.name,
                    email = account.email,
                )
            },
        ) ?: return dismissed(prompt)
        val host = currentHost() ?: return dismissed(prompt)
        return pending(prompt) { complete ->
            host.selectIdentityAccount(
                IdentityCredentialAccountPrompt(
                    identity = identity,
                    providerName = providerName,
                    providerDomain = providerDomain,
                    accounts = accounts,
                ),
            ) { id ->
                val selected = id?.takeIf { candidate ->
                    accounts.any { account -> account.id == candidate } &&
                        CredentialPromptRules.remainsCurrent(identity, currentSecureIdentity())
                }
                complete(selected, prompt::confirm)
            }
        }
    }

    fun onIdentityPrivacyPolicy(
        prompt: GeckoSession.PromptDelegate.IdentityCredential.PrivacyPolicyPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val identity = currentSecureIdentity() ?: return dismissed(prompt)
        val request = CredentialPromptRules.identityPrivacyPrompt(
            identity = identity,
            providerName = prompt.providerDomain,
            providerDomain = prompt.providerDomain,
            relyingPartyHost = prompt.host,
            privacyPolicyUrl = prompt.privacyPolicyUrl,
            termsOfServiceUrl = prompt.termsOfServiceUrl,
        ) ?: return dismissed(prompt)
        val host = currentHost() ?: return dismissed(prompt)
        return pending(prompt) { complete ->
            host.confirmIdentityPrivacyPolicy(request) { accepted ->
                val safeAccepted = accepted &&
                    CredentialPromptRules.remainsCurrent(identity, currentSecureIdentity())
                complete(safeAccepted) { value -> prompt.confirm(value) }
            }
        }
    }

    private fun <T : GeckoSession.PromptDelegate.BasePrompt> dismissed(
        prompt: T,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> =
        GeckoResult.fromValue(prompt.dismiss())

    private fun <T : GeckoSession.PromptDelegate.BasePrompt, V : Any> pending(
        prompt: T,
        dispatch: (
            (
                value: V?,
                confirm: (V) -> GeckoSession.PromptDelegate.PromptResponse,
            ) -> Unit
        ) -> Unit,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        val completed = AtomicBoolean(false)
        dispatch { value, confirm ->
            if (!completed.compareAndSet(false, true)) return@dispatch
            val response = if (value == null) {
                prompt.dismiss()
            } else {
                confirm(value)
            }
            result.complete(response)
        }
        return result
    }
}
