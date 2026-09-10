@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.FederatedLoginOffer
import dev.sk2andy.materialbrowser.browser.FederatedLoginPromptChoice
import dev.sk2andy.materialbrowser.browser.CaptchaCompatibilityOffer
import dev.sk2andy.materialbrowser.browser.CaptchaCompatibilityPromptChoice
import dev.sk2andy.materialbrowser.browser.PageTranslationRecoveryAction

@Composable
internal fun BrowserOfferSnackbarEffects(
    controller: BrowserController,
    hostState: SnackbarHostState,
) {
    val popupBlockedMessage = stringResource(R.string.popup_blocked)
    val openPopupLabel = stringResource(R.string.action_open_popup)
    val federatedLoginDetectedMessage = stringResource(
        R.string.federated_login_detected,
        controller.federatedLoginOffer?.provider?.displayName.orEmpty(),
    )
    val federatedLoginOptionsLabel = stringResource(R.string.federated_login_options)
    val captchaDetectedMessage = stringResource(
        R.string.captcha_compatibility_detected,
        controller.captchaCompatibilityOffer?.provider?.displayName.orEmpty(),
    )
    val captchaOptionsLabel = stringResource(R.string.captcha_compatibility_options)
    val translationFailedMessage = stringResource(
        R.string.page_translation_failed,
        controller.pageTranslationRecoveryOffer?.provider?.displayName.orEmpty(),
    )
    val tryYandexLabel = stringResource(R.string.action_try_yandex_translation)
    val openOriginalLabel = stringResource(R.string.action_open_original_page)
    val blockedPopupOffer = controller.blockedPopupOffer
    LaunchedEffect(blockedPopupOffer?.token) {
        val offer = blockedPopupOffer ?: return@LaunchedEffect
        var opened = false
        try {
            val result = hostState.showSnackbar(
                message = popupBlockedMessage,
                actionLabel = openPopupLabel,
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                opened = true
                controller.openBlockedPopup(offer.token)
            }
        } finally {
            if (!opened) controller.dismissBlockedPopup(offer.token)
        }
    }
    val pageTranslationRecoveryOffer = controller.pageTranslationRecoveryOffer
    LaunchedEffect(pageTranslationRecoveryOffer?.token) {
        val offer = pageTranslationRecoveryOffer ?: return@LaunchedEffect
        var recovered = false
        try {
            val result = hostState.showSnackbar(
                message = translationFailedMessage,
                actionLabel = when (offer.action) {
                    PageTranslationRecoveryAction.TryYandex -> tryYandexLabel
                    PageTranslationRecoveryAction.OpenOriginal -> openOriginalLabel
                },
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                recovered = true
                controller.recoverPageTranslation(offer.token)
            }
        } finally {
            if (!recovered) controller.dismissPageTranslationRecovery(offer.token)
        }
    }
    val federatedLoginOffer = controller.federatedLoginOffer
    LaunchedEffect(federatedLoginOffer?.token) {
        val offer = federatedLoginOffer?.takeUnless(FederatedLoginOffer::showDialog)
            ?: return@LaunchedEffect
        var optionsOpened = false
        try {
            val result = hostState.showSnackbar(
                message = federatedLoginDetectedMessage,
                actionLabel = federatedLoginOptionsLabel,
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                optionsOpened = true
                controller.showFederatedLoginOptions(offer.token)
            }
        } finally {
            if (!optionsOpened) {
                controller.respondToFederatedLoginOffer(
                    offer.token,
                    FederatedLoginPromptChoice.Deny,
                )
            }
        }
    }
    val captchaCompatibilityOffer = controller.captchaCompatibilityOffer
    LaunchedEffect(captchaCompatibilityOffer?.token) {
        val offer = captchaCompatibilityOffer?.takeUnless(
            CaptchaCompatibilityOffer::showDialog,
        ) ?: return@LaunchedEffect
        var optionsOpened = false
        try {
            val result = hostState.showSnackbar(
                message = captchaDetectedMessage,
                actionLabel = captchaOptionsLabel,
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                optionsOpened = true
                controller.showCaptchaCompatibilityOptions(offer.token)
            }
        } finally {
            if (!optionsOpened) {
                controller.respondToCaptchaCompatibilityOffer(
                    offer.token,
                    CaptchaCompatibilityPromptChoice.Deny,
                )
            }
        }
    }
}
