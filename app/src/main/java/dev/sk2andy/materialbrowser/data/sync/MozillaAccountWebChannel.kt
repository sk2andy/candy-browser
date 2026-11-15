/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package dev.sk2andy.materialbrowser.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mozilla.components.service.fxa.FxaAuthData
import mozilla.components.service.fxa.FxaService
import mozilla.components.service.fxa.manager.FxaAccountManager
import mozilla.components.service.fxa.sync.toSyncEngines
import mozilla.components.service.fxa.toAuthType
import org.json.JSONArray
import org.json.JSONObject

// Adapted from Mozilla Android Components 160.0 FxaWebChannelFeature.
internal class MozillaAccountWebChannel(
    private val accountManager: FxaAccountManager,
    private val scope: CoroutineScope,
    private val onError: () -> Unit,
    private val onLogout: suspend () -> Unit,
) {
    /** The Gecko bridge must verify the sender origin and active login session before calling this. */
    fun handle(message: String): String? {
        val request = MozillaAccountWebChannelRules.parse(message) ?: return null
        return try {
            val data = when (request.command) {
                "fxaccounts:fxa_status" -> MozillaAccountWebChannelRules.status(
                    engines = accountManager.supportedSyncEngines().orEmpty().map { it.nativeName },
                    signedInUser = accountManager.authenticatedAccount()?.getSignedInUserForWebChannel(),
                )
                "fxaccounts:can_link_account" -> {
                    val profile = accountManager.accountProfile()
                    JSONObject().put("ok", MozillaAccountWebChannelRules.canLinkAccount(
                        request.data,
                        profile?.uid,
                        profile?.email,
                    ))
                }
                "fxaccounts:oauth_login" -> {
                    val authData = MozillaAccountWebChannelRules.authData(request.data) ?: return null
                    execute { accountManager.finishAuthentication(authData) }
                    return null
                }
                "fxaccounts:login" -> {
                    val payload = request.data?.toString() ?: return null
                    execute { accountManager.handleWebChannelLogin(payload) }
                    return null
                }
                "fxaccounts:change_password" -> {
                    val payload = request.data?.toString() ?: return null
                    execute { accountManager.handleWebChannelPasswordChange(payload) }
                    return null
                }
                "fxaccounts:logout", "fxaccounts:delete" -> {
                    execute { onLogout() }
                    return null
                }
                "fxaccounts:sync_preferences" -> {
                    val canHandle = accountManager.authenticatedAccount() != null
                    JSONObject().put("ok", canHandle)
                }
                else -> JSONObject().put("error", "Unsupported Mozilla account command")
            }
            MozillaAccountWebChannelRules.response(request, data).toString()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            onError()
            null
        }
    }

    private fun execute(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                onError()
            }
        }
    }
}

internal object MozillaAccountWebChannelRules {
    private const val CHANNEL_ID = "account_updates"
    private const val MAX_MESSAGE_LENGTH = 64 * 1024

    data class Request(val command: String, val messageId: String, val data: JSONObject?)

    fun parse(message: String): Request? {
        if (message.length > MAX_MESSAGE_LENGTH) return null
        return runCatching {
            val envelope = JSONObject(message)
            if (envelope.opt("id") != CHANNEL_ID) return null
            val payload = envelope.getJSONObject("message")
            val command = payload.opt("command") as? String ?: return null
            val messageId = when (val value = payload.opt("messageId")) {
                null -> ""
                is String -> value
                else -> return null
            }
            val data = when (val value = payload.opt("data")) {
                null -> null
                JSONObject.NULL -> null
                is JSONObject -> value
                else -> return null
            }
            Request(command, messageId, data)
        }.getOrNull()
    }

    fun response(request: Request, data: JSONObject): JSONObject = JSONObject()
        .put("id", CHANNEL_ID)
        .put("message", JSONObject()
            .put("command", request.command)
            .put("messageId", request.messageId)
            .put("data", data))

    fun status(engines: List<String>, signedInUser: String?): JSONObject = JSONObject()
        .put("capabilities", JSONObject()
            .put("engines", JSONArray(engines))
            .put("can_link_account_uid", true)
            .put("services", JSONObject().put("sync", JSONObject()
                .put("scope", JSONArray(FxaService.Sync.requiredScopes.map { it.value })))))
        .put("signedInUser", signedInUser?.let(::JSONObject) ?: JSONObject.NULL)

    fun canLinkAccount(data: JSONObject?, currentUid: String?, currentEmail: String?): Boolean {
        if (currentUid == null) return true
        val uid = data?.opt("uid") as? String
        return if (!uid.isNullOrEmpty()) {
            uid == currentUid
        } else {
            currentEmail != null && data?.opt("email") == currentEmail
        }
    }

    fun authData(data: JSONObject?): FxaAuthData? {
        data ?: return null
        val action = data.opt("action") as? String ?: return null
        val code = data.opt("code") as? String ?: return null
        val state = data.opt("state") as? String ?: return null
        if (action.isBlank() || code.isBlank() || state.isBlank()) return null
        val declined = when (val value = data.opt("declinedSyncEngines")) {
            null -> null
                JSONObject.NULL -> null
            is JSONArray -> (0 until value.length()).map { value.opt(it) as? String ?: return null }
            else -> return null
        }
        return FxaAuthData(
            authType = action.toAuthType(),
            code = code,
            state = state,
            declinedEngines = declined?.toSyncEngines(),
        )
    }
}
