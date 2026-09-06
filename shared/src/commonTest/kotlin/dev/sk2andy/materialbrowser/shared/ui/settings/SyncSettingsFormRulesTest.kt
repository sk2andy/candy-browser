package dev.sk2andy.materialbrowser.shared.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SyncSettingsFormRulesTest {
    @Test
    fun `valid independent credentials pass`() {
        assertNull(
            SyncSettingsFormRules.validate(
                endpoint = "https://sync.example",
                username = "candy",
                serverPassword = "server-secret",
                passphrase = "a sufficiently long passphrase",
                confirmation = "a sufficiently long passphrase",
                deviceName = "iPhone",
            ),
        )
    }

    @Test
    fun `reused password is rejected`() {
        val sharedSecret = "same-secret-value"

        assertEquals(
            SyncSettingsValidationError.PasswordReuse,
            SyncSettingsFormRules.validate(
                endpoint = "https://sync.example",
                username = "candy",
                serverPassword = sharedSecret,
                passphrase = sharedSecret,
                confirmation = sharedSecret,
                deviceName = "iPhone",
            ),
        )
    }
}
