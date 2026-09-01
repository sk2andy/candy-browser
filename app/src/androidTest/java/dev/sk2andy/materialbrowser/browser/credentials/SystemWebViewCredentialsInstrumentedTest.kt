package dev.sk2andy.materialbrowser.browser.credentials

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class SystemWebViewCredentialsInstrumentedTest {
    @Test
    fun grantsPermissionRequiredByBrowserWebAuthentication() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission(Manifest.permission.CREDENTIAL_MANAGER_SET_ORIGIN),
        )
    }
}
