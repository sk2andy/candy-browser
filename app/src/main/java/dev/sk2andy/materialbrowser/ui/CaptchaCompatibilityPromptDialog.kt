package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.CaptchaCompatibilityOffer
import dev.sk2andy.materialbrowser.browser.CaptchaCompatibilityPromptChoice

internal object CaptchaCompatibilityPromptTestTags {
    const val Dialog = "captcha-compatibility-dialog"
    const val AllowForTab = "captcha-compatibility-allow-tab"
    const val AllowForProfile = "captcha-compatibility-allow-profile"
    const val Deny = "captcha-compatibility-deny"
}

@Composable
internal fun CaptchaCompatibilityPromptDialog(
    offer: CaptchaCompatibilityOffer,
    onChoice: (CaptchaCompatibilityPromptChoice) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onChoice(CaptchaCompatibilityPromptChoice.Deny) },
        modifier = Modifier.testTag(CaptchaCompatibilityPromptTestTags.Dialog),
        title = {
            Text(
                stringResource(
                    R.string.captcha_compatibility_dialog_title,
                    offer.provider.displayName,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(
                        R.string.captcha_compatibility_dialog_message,
                        offer.pageHost,
                    ),
                )
                if (offer.isPrivate) {
                    Text(
                        stringResource(R.string.captcha_compatibility_private_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onChoice(CaptchaCompatibilityPromptChoice.AllowForTab) },
                    modifier = Modifier.testTag(CaptchaCompatibilityPromptTestTags.AllowForTab),
                ) {
                    Text(stringResource(R.string.captcha_compatibility_allow_for_tab))
                }
                if (!offer.isPrivate) {
                    FilledTonalButton(
                        onClick = {
                            onChoice(CaptchaCompatibilityPromptChoice.AllowForProfile)
                        },
                        modifier = Modifier.testTag(
                            CaptchaCompatibilityPromptTestTags.AllowForProfile,
                        ),
                    ) {
                        Text(stringResource(R.string.captcha_compatibility_allow_for_profile))
                    }
                }
                TextButton(
                    onClick = { onChoice(CaptchaCompatibilityPromptChoice.Deny) },
                    modifier = Modifier.testTag(CaptchaCompatibilityPromptTestTags.Deny),
                ) {
                    Text(stringResource(R.string.captcha_compatibility_deny))
                }
            }
        },
    )
}
