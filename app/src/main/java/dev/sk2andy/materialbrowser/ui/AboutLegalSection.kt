package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.legal.CandyLegalSources
import dev.sk2andy.materialbrowser.legal.ThirdPartyComponent
import dev.sk2andy.materialbrowser.legal.ThirdPartyNotice

internal object AboutLegalTestTags {
    const val Section = "about_legal_section"
    const val Imprint = "about_legal_imprint"
    const val OpenSource = "about_legal_open_source"
    const val Uassets = "about_legal_uassets"
    const val ImprintDialog = "about_legal_imprint_dialog"
    const val OpenSourceDialog = "about_legal_open_source_dialog"
    const val UassetsDialog = "about_legal_uassets_dialog"
    const val GitHubLink = "about_legal_github_link"
    const val UassetsSourceLink = "about_legal_uassets_source_link"
    const val UassetsLicenseLink = "about_legal_uassets_license_link"
    const val BundledNoticesLink = "about_legal_bundled_notices_link"
    const val BundledNoticesDialog = "about_legal_bundled_notices_dialog"

    fun licenseLink(component: ThirdPartyComponent): String =
        "about_legal_license_${component.name.lowercase()}"
}

private enum class AboutLegalDialog {
    Imprint,
    OpenSource,
    Uassets,
    BundledNotices,
}

@Composable
internal fun AboutLegalSection(
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
) {
    var dialog by rememberSaveable { mutableStateOf<AboutLegalDialog?>(null) }

    Column(modifier = modifier.testTag(AboutLegalTestTags.Section)) {
        if (showTitle) {
            Text(
                stringResource(R.string.settings_section_about_legal),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column {
                AboutLegalRow(
                    title = stringResource(R.string.settings_imprint_title),
                    summary = stringResource(R.string.settings_imprint_summary),
                    tag = AboutLegalTestTags.Imprint,
                    onClick = { dialog = AboutLegalDialog.Imprint },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                AboutLegalRow(
                    title = stringResource(R.string.settings_open_source_title),
                    summary = stringResource(R.string.settings_open_source_summary),
                    tag = AboutLegalTestTags.OpenSource,
                    onClick = { dialog = AboutLegalDialog.OpenSource },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                AboutLegalRow(
                    title = stringResource(R.string.settings_uassets_source_title),
                    summary = stringResource(R.string.settings_uassets_source_summary),
                    tag = AboutLegalTestTags.Uassets,
                    onClick = { dialog = AboutLegalDialog.Uassets },
                )
            }
        }
    }

    when (dialog) {
        AboutLegalDialog.Imprint -> ImprintDialog(
            onOpenUrl = {
                dialog = null
                onOpenUrl(CandyLegalSources.GITHUB_PROFILE_URL)
            },
            onDismiss = { dialog = null },
        )
        AboutLegalDialog.OpenSource -> OpenSourceDialog(
            onOpenUrl = { url ->
                dialog = null
                onOpenUrl(url)
            },
            onOpenBundledNotices = { dialog = AboutLegalDialog.BundledNotices },
            onDismiss = { dialog = null },
        )
        AboutLegalDialog.Uassets -> UassetsDialog(
            onOpenUrl = { url ->
                dialog = null
                onOpenUrl(url)
            },
            onDismiss = { dialog = null },
        )
        AboutLegalDialog.BundledNotices -> BundledNoticesDialog(
            onDismiss = { dialog = AboutLegalDialog.OpenSource },
        )
        null -> Unit
    }
}

@Composable
private fun AboutLegalRow(
    title: String,
    summary: String,
    tag: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 64.dp)
            .testTag(tag),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ImprintDialog(onOpenUrl: () -> Unit, onDismiss: () -> Unit) {
    LegalDialog(
        title = stringResource(R.string.settings_imprint_title),
        tag = AboutLegalTestTags.ImprintDialog,
        onDismiss = onDismiss,
    ) {
        Text(
            stringResource(R.string.about_imprint_body, CandyLegalSources.DEVELOPER_NAME),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LegalLinkButton(
            text = stringResource(R.string.about_github_action),
            tag = AboutLegalTestTags.GitHubLink,
            onClick = onOpenUrl,
        )
    }
}

@Composable
private fun OpenSourceDialog(
    onOpenUrl: (String) -> Unit,
    onOpenBundledNotices: () -> Unit,
    onDismiss: () -> Unit,
) {
    LegalDialog(
        title = stringResource(R.string.settings_open_source_title),
        tag = AboutLegalTestTags.OpenSourceDialog,
        onDismiss = onDismiss,
    ) {
        Text(
            stringResource(R.string.about_open_source_intro),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        CandyLegalSources.thirdPartyNotices.forEachIndexed { index, notice ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            OpenSourceLicenseEntry(notice = notice, onOpenUrl = onOpenUrl)
        }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 10.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        LegalLinkButton(
            text = stringResource(R.string.about_bundled_notices_action),
            tag = AboutLegalTestTags.BundledNoticesLink,
            onClick = onOpenBundledNotices,
        )
    }
}

@Composable
private fun BundledNoticesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val notices = remember(context) {
        runCatching {
            context.assets.open("third_party_notices.txt")
                .bufferedReader()
                .use { reader -> reader.readText() }
        }.getOrNull()
    }
    LegalDialog(
        title = stringResource(R.string.about_bundled_notices_title),
        tag = AboutLegalTestTags.BundledNoticesDialog,
        onDismiss = onDismiss,
    ) {
        Text(
            text = notices ?: stringResource(R.string.about_notices_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OpenSourceLicenseEntry(notice: ThirdPartyNotice, onOpenUrl: (String) -> Unit) {
    Text(
        text = notice.component.displayName(),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = notice.licenseName,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LegalLinkButton(
        text = stringResource(
            if (notice.component == ThirdPartyComponent.GoogleCodeScanner) {
                R.string.about_terms_action
            } else {
                R.string.about_license_action
            },
            notice.component.displayName(),
        ),
        tag = AboutLegalTestTags.licenseLink(notice.component),
        onClick = { onOpenUrl(notice.licenseUrl) },
    )
}

@Composable
private fun UassetsDialog(onOpenUrl: (String) -> Unit, onDismiss: () -> Unit) {
    LegalDialog(
        title = stringResource(R.string.settings_uassets_source_title),
        tag = AboutLegalTestTags.UassetsDialog,
        onDismiss = onDismiss,
    ) {
        Text(
            stringResource(R.string.about_uassets_body, CandyLegalSources.UASSETS_SHORT_REVISION),
            style = MaterialTheme.typography.bodyMedium,
        )
        LegalLinkButton(
            text = stringResource(R.string.about_source_action),
            tag = AboutLegalTestTags.UassetsSourceLink,
            onClick = { onOpenUrl(CandyLegalSources.UASSETS_SOURCE_URL) },
        )
        LegalLinkButton(
            text = stringResource(
                R.string.about_license_action,
                stringResource(R.string.about_license_uassets),
            ),
            tag = AboutLegalTestTags.UassetsLicenseLink,
            onClick = { onOpenUrl(CandyLegalSources.UASSETS_LICENSE_URL) },
        )
    }
}

@Composable
private fun LegalDialog(
    title: String,
    tag: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(tag),
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                content = content,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_done))
            }
        },
    )
}

@Composable
private fun LegalLinkButton(text: String, tag: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.testTag(tag),
    ) {
        Text(text)
        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ThirdPartyComponent.displayName(): String = when (this) {
    ThirdPartyComponent.AndroidX -> stringResource(R.string.about_license_androidx)
    ThirdPartyComponent.Kotlin -> stringResource(R.string.about_license_kotlin)
    ThirdPartyComponent.MaterialIcons -> stringResource(R.string.about_license_material_icons)
    ThirdPartyComponent.GoogleOpenSource -> stringResource(R.string.about_license_google_oss)
    ThirdPartyComponent.GoogleCodeScanner -> stringResource(R.string.about_license_google_scanner)
    ThirdPartyComponent.EasyList -> stringResource(R.string.about_license_easylist)
    ThirdPartyComponent.Uassets -> stringResource(R.string.about_license_uassets)
}
