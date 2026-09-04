package dev.sk2andy.materialbrowser.browser.credentials

import android.Manifest
import android.content.pm.PackageManager
import android.webkit.WebView
import androidx.credentials.CredentialManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class SystemWebViewCredentialsInstrumentedTest {
    @Test
    fun bundlesCredentialManagerRuntime() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        assertNotNull(CredentialManager.create(context))
    }

    @Test
    fun grantsPermissionRequiredByBrowserWebAuthentication() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission(Manifest.permission.CREDENTIAL_MANAGER_SET_ORIGIN),
        )
    }

    @Test
    fun configuresBrowserWebAuthenticationWhenProviderSupportsIt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        var featureSupported = false
        var configuredSupport: WebViewCredentialSupport? = null
        var appliedSupport = WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_NONE

        instrumentation.runOnMainSync {
            featureSupported = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)
            if (featureSupported) {
                val webView = WebView(context)
                configuredSupport = SystemWebViewCredentials.configure(webView)
                appliedSupport = WebSettingsCompat.getWebAuthenticationSupport(webView.settings)
                webView.destroy()
            }
        }

        assumeTrue("Installed WebView provider lacks WebAuthn", featureSupported)
        assertEquals(
            WebAuthenticationSupport.ENABLED_FOR_BROWSER,
            configuredSupport?.webAuthentication,
        )
        assertEquals(
            WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER,
            appliedSupport,
        )
    }
}
