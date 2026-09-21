package dev.sk2andy.materialbrowser.browser

import android.os.Process
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserMediaControllerAuthRulesTest {
    @Test
    fun `own package system and media notification controllers are accepted`() {
        assertTrue(
            BrowserMediaControllerAuthRules.accepts(
                ownPackageName = "dev.sk2andy.materialbrowser",
                controllerPackageName = "dev.sk2andy.materialbrowser",
                controllerUid = 12_345,
                isTrustedSystemController = false,
                isMediaNotificationController = false,
            ),
        )
        assertTrue(
            BrowserMediaControllerAuthRules.accepts(
                ownPackageName = "dev.sk2andy.materialbrowser",
                controllerPackageName = "android",
                controllerUid = Process.SYSTEM_UID,
                isTrustedSystemController = false,
                isMediaNotificationController = false,
            ),
        )
        assertTrue(
            BrowserMediaControllerAuthRules.accepts(
                ownPackageName = "dev.sk2andy.materialbrowser",
                controllerPackageName = "notification-controller",
                controllerUid = 12_345,
                isTrustedSystemController = false,
                isMediaNotificationController = true,
            ),
        )
    }

    @Test
    fun `untrusted third party controller is rejected`() {
        assertFalse(
            BrowserMediaControllerAuthRules.accepts(
                ownPackageName = "dev.sk2andy.materialbrowser",
                controllerPackageName = "com.example.remote",
                controllerUid = 12_345,
                isTrustedSystemController = false,
                isMediaNotificationController = false,
            ),
        )
    }
}
