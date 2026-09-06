package dev.sk2andy.materialbrowser.shared.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sk2andy.materialbrowser.shared.ui.PlatformProfileEmoji
import dev.sk2andy.materialbrowser.sync.SyncConnectionSettings
import dev.sk2andy.materialbrowser.sync.SyncDeviceIconDefinition
import dev.sk2andy.materialbrowser.sync.SyncEnrollmentOutcome
import dev.sk2andy.materialbrowser.sync.SyncProfile
import dev.sk2andy.materialbrowser.sync.SyncStatus

private val SYNC_ACCENT_HUES = listOf(0, 36, 72, 108, 144, 180, 216, 252, 288, 312)

data class SyncLocalProfileOption(
    val id: String,
    val emoji: String,
    val isCurrent: Boolean,
)

/** Presentation-only state. Protocol ownership remains in CandySyncRepository. */
data class SyncSettingsUiState(
    val settings: SyncConnectionSettings? = null,
    val status: SyncStatus = SyncStatus.Unconfigured,
    val profiles: List<SyncProfile> = emptyList(),
    val pendingCount: Int = 0,
    val lastSuccessAt: String? = null,
    val icons: List<SyncDeviceIconDefinition> = listOf(
        SyncDeviceIconDefinition("candy", "🍬", "Candy"),
    ),
    val localProfiles: List<SyncLocalProfileOption> = listOf(
        SyncLocalProfileOption("default", "🍬", true),
    ),
    val isBusy: Boolean = false,
)

interface SyncSettingsActionSink {
    fun configure(settings: SyncConnectionSettings): Boolean

    fun enroll(
        serverPassword: CharArray,
        passphrase: CharArray,
        onComplete: (SyncEnrollmentOutcome) -> Unit,
    )

    fun refresh()
}

internal enum class SyncSettingsValidationError {
    PassphraseMismatch,
    PassphraseTooShort,
    PasswordReuse,
    RequiredFields,
}

internal object SyncSettingsFormRules {
    fun validate(
        endpoint: String,
        username: String,
        serverPassword: String,
        passphrase: String,
        confirmation: String,
        deviceName: String,
    ): SyncSettingsValidationError? = when {
        passphrase != confirmation -> SyncSettingsValidationError.PassphraseMismatch
        passphrase.length < 16 -> SyncSettingsValidationError.PassphraseTooShort
        passphrase == serverPassword -> SyncSettingsValidationError.PasswordReuse
        endpoint.isBlank() || username.isBlank() || serverPassword.isBlank() ||
            passphrase.isBlank() || deviceName.isBlank() -> SyncSettingsValidationError.RequiredFields
        else -> null
    }
}

@Composable
fun SyncSettingsPage(
    state: SyncSettingsUiState,
    actions: SyncSettingsActionSink,
    onBack: () -> Unit,
) {
    val configured = state.settings
    var endpoint by rememberSaveable(configured?.endpoint) {
        mutableStateOf(configured?.endpoint.orEmpty())
    }
    var username by rememberSaveable(configured?.username) {
        mutableStateOf(configured?.username.orEmpty())
    }
    var deviceName by rememberSaveable(configured?.deviceName) {
        mutableStateOf(configured?.deviceName.orEmpty())
    }
    var localProfileId by rememberSaveable(configured?.localProfileId, state.localProfiles) {
        mutableStateOf(
            configured?.localProfileId?.takeIf { id -> state.localProfiles.any { it.id == id } }
                ?: state.localProfiles.firstOrNull { it.isCurrent }?.id
                ?: state.localProfiles.first().id,
        )
    }
    var iconCatalogId by rememberSaveable(configured?.iconCatalogId, state.icons) {
        mutableStateOf(
            configured?.iconCatalogId?.takeIf { id -> state.icons.any { it.id == id } }
                ?: state.icons.first().id,
        )
    }
    var iconAccentHue by rememberSaveable(configured?.iconAccentHue) {
        mutableStateOf(configured?.iconAccentHue ?: 312)
    }
    var serverPassword by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var iconMenuExpanded by remember { mutableStateOf(false) }
    var profileMenuExpanded by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val selectedIcon = state.icons.firstOrNull { it.id == iconCatalogId } ?: state.icons.first()
    val selectedProfile = state.localProfiles.firstOrNull { it.id == localProfileId }
        ?: state.localProfiles.first()

    SettingsPage(title = "Synchronisierung", backContentDescription = "Zurück", onBack = onBack) {
        SyncStatusCard(state)
        SectionTitle("Verbindung")
        OutlinedTextField(
            value = endpoint,
            onValueChange = { endpoint = it },
            label = { Text("Server-Adresse") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Benutzername") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SecretTextField(
            value = serverPassword,
            onValueChange = { serverPassword = it },
            label = "Server-Passwort",
            supportingText = "Nur für die Anmeldung am eigenen Candy-Sync-Server.",
        )

        SectionTitle("Dieses Gerät")
        Box {
            SyncChoiceRow(
                emoji = selectedProfile.emoji,
                title = "Lokales Profil",
                summary = if (selectedProfile.isCurrent) "Aktuelles Profil" else "Vorhandenes Profil",
                onClick = { profileMenuExpanded = true },
            )
            DropdownMenu(
                expanded = profileMenuExpanded,
                onDismissRequest = { profileMenuExpanded = false },
            ) {
                state.localProfiles.forEach { profile ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PlatformProfileEmoji(
                                    emoji = profile.emoji,
                                    fontSize = 24.sp,
                                    modifier = Modifier.size(28.dp),
                                )
                                Text(
                                    if (profile.isCurrent) "Aktuelles Profil" else "Vorhandenes Profil",
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }
                        },
                        onClick = {
                            localProfileId = profile.id
                            profileMenuExpanded = false
                        },
                    )
                }
            }
        }
        OutlinedTextField(
            value = deviceName,
            onValueChange = { deviceName = it },
            label = { Text("Gerätename") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Box {
            SyncChoiceRow(
                emoji = selectedIcon.emoji,
                title = "Geräte-Icon",
                summary = selectedIcon.label,
                modifier = Modifier.padding(top = 8.dp),
                onClick = { iconMenuExpanded = true },
            )
            DropdownMenu(
                expanded = iconMenuExpanded,
                onDismissRequest = { iconMenuExpanded = false },
            ) {
                state.icons.forEach { icon ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PlatformProfileEmoji(
                                    emoji = icon.emoji,
                                    fontSize = 24.sp,
                                    modifier = Modifier.size(28.dp),
                                )
                                Text(icon.label, modifier = Modifier.padding(start = 12.dp))
                            }
                        },
                        onClick = {
                            iconCatalogId = icon.id
                            iconMenuExpanded = false
                        },
                    )
                }
            }
        }
        SyncAccentColorPicker(iconAccentHue) { iconAccentHue = it }

        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Recovery-Passphrase sicher aufbewahren", fontWeight = FontWeight.Bold)
                Text(
                    "Sie verschlüsselt deine Sync-Daten Ende zu Ende. Candy speichert sie nicht und kann sie nicht wiederherstellen.",
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        SecretTextField(passphrase, { passphrase = it }, "Recovery-Passphrase")
        SecretTextField(confirmation, { confirmation = it }, "Passphrase bestätigen")
        feedback?.let { message ->
            Text(
                message,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                enabled = state.status != SyncStatus.Unconfigured && !submitting && !state.isBusy,
                onClick = actions::refresh,
            ) { Text("Aktualisieren") }
            Button(
                enabled = !submitting && !state.isBusy,
                onClick = {
                    feedback = SyncSettingsFormRules.validate(
                        endpoint, username, serverPassword, passphrase, confirmation, deviceName,
                    )?.message
                    if (feedback != null) return@Button
                    if (!actions.configure(
                            SyncConnectionSettings(
                                endpoint = endpoint,
                                username = username,
                                deviceName = deviceName,
                                iconCatalogId = iconCatalogId,
                                iconAccentHue = iconAccentHue,
                                localProfileId = localProfileId,
                            ),
                        )
                    ) {
                        feedback = "Die Konfiguration ist ungültig."
                        return@Button
                    }
                    submitting = true
                    val passwordChars = serverPassword.toCharArray()
                    val passphraseChars = passphrase.toCharArray()
                    serverPassword = ""
                    passphrase = ""
                    confirmation = ""
                    actions.enroll(passwordChars, passphraseChars) { outcome ->
                        submitting = false
                        feedback = outcome.message
                    }
                },
            ) { Text("Verbinden") }
        }

        if (state.profiles.isNotEmpty()) {
            SectionTitle("Geräte")
            state.profiles.forEach { profile ->
                val icon = state.icons.firstOrNull { it.id == profile.icon.catalogId }
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        PlatformProfileEmoji(
                            emoji = icon?.emoji ?: "🍬",
                            fontSize = 26.sp,
                            modifier = Modifier.size(32.dp),
                        )
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(profile.displayName, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${profile.tabs.size} Tabs · zuletzt ${profile.lastSeenAt}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(value: String) {
    Text(
        value,
        modifier = Modifier.padding(start = 18.dp, top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SyncChoiceRow(
    emoji: String,
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            PlatformProfileEmoji(
                emoji = emoji,
                fontSize = 26.sp,
                modifier = Modifier.size(32.dp),
            )
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(title)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SyncAccentColorPicker(
    selectedHue: Int,
    onHueSelected: (Int) -> Unit,
) {
    val hues = if (selectedHue in SYNC_ACCENT_HUES) SYNC_ACCENT_HUES else {
        SYNC_ACCENT_HUES.dropLast(1) + selectedHue.coerceIn(0, 359)
    }
    Text("Akzentfarbe", modifier = Modifier.padding(start = 18.dp, top = 16.dp, bottom = 4.dp))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        hues.forEach { hue ->
            val selected = hue == selectedHue
            Box(
                modifier = Modifier.size(48.dp).selectable(
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = { onHueSelected(hue) },
                ),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.size(34.dp).border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    ),
                    shape = CircleShape,
                    color = Color.hsv(hue.toFloat(), 0.42f, 0.86f),
                ) {}
            }
        }
    }
}

@Composable
private fun SecretTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String? = null,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supportingText?.let { text -> { Text(text) } },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "Passwort ausblenden" else "Passwort anzeigen",
                )
            }
        },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SyncStatusCard(state: SyncSettingsUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("Status", style = MaterialTheme.typography.titleSmall)
            Text(
                state.status.label,
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${state.pendingCount} ausstehend · zuletzt ${state.lastSuccessAt ?: "nie"}",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val SyncStatus.label: String
    get() = when (this) {
        SyncStatus.Unconfigured -> "Nicht verbunden"
        SyncStatus.Enrolling -> "Wird verbunden"
        SyncStatus.Ready -> "Bereit"
        SyncStatus.Syncing -> "Wird synchronisiert"
        SyncStatus.Offline -> "Offline"
        SyncStatus.AuthError -> "Anmeldung fehlgeschlagen"
        SyncStatus.CryptoError -> "Entschlüsselung fehlgeschlagen"
        SyncStatus.Incompatible -> "Server nicht kompatibel"
    }

private val SyncSettingsValidationError.message: String
    get() = when (this) {
        SyncSettingsValidationError.PassphraseMismatch -> "Die Passphrasen stimmen nicht überein."
        SyncSettingsValidationError.PassphraseTooShort -> "Die Passphrase muss mindestens 16 Zeichen lang sein."
        SyncSettingsValidationError.PasswordReuse -> "Server-Passwort und Passphrase müssen verschieden sein."
        SyncSettingsValidationError.RequiredFields -> "Bitte alle Pflichtfelder ausfüllen."
    }

private val SyncEnrollmentOutcome.message: String?
    get() = when (this) {
        SyncEnrollmentOutcome.Enrolled -> null
        SyncEnrollmentOutcome.InvalidConfiguration -> "Die Konfiguration ist ungültig."
        SyncEnrollmentOutcome.AuthenticationFailed -> "Benutzername oder Server-Passwort ist falsch."
        SyncEnrollmentOutcome.WrongPassphrase -> "Die Recovery-Passphrase ist falsch."
        SyncEnrollmentOutcome.IncompatibleServer -> "Der Server ist nicht kompatibel."
        SyncEnrollmentOutcome.Failed -> "Synchronisierung fehlgeschlagen."
    }
