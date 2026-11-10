package dev.sk2andy.materialbrowser.browser.systemwebview.credentials

import android.Manifest
import android.content.pm.PackageManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.credentials.CredentialManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
    fun securePagesSeeWebAuthnAsUnavailable() {
        assertWebAuthnUnavailable(configureCredentials = true)
        assertWebAuthnUnavailable(configureCredentials = false)
    }

    private fun assertWebAuthnUnavailable(configureCredentials: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val loaded = CountDownLatch(1)
        val evaluated = CountDownLatch(1)
        val result = AtomicReference<String>()
        lateinit var webView: WebView
        instrumentation.runOnMainSync {
            webView = WebView(instrumentation.targetContext)
            webView.settings.javaScriptEnabled = true
            if (configureCredentials) SystemWebViewCredentials.configure(webView)
            webView.webViewClient = PageLoadClient(loaded)
            webView.loadDataWithBaseURL(
                "https://example.com/",
                "<html><script>window.authAtStart = typeof PublicKeyCredential</script>" +
                    "<iframe srcdoc=\"<p>frame</p>\"></iframe></html>",
                "text/html", "UTF-8", null,
            )
        }
        try {
            assertTrue(loaded.await(20, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                webView.evaluateJavascript(
                    "JSON.stringify([isSecureContext, window.authAtStart, typeof navigator.credentials, " +
                        "typeof document.querySelector('iframe').contentWindow.PublicKeyCredential])",
                ) { result.set(it); evaluated.countDown() }
            }
            assertTrue(evaluated.await(10, TimeUnit.SECONDS))
            assertEquals("\"[true,\\\"undefined\\\",\\\"object\\\",\\\"undefined\\\"]\"", result.get())
        } finally {
            instrumentation.runOnMainSync { webView.destroy() }
        }
    }
    private class PageLoadClient(private val loaded: CountDownLatch) : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            loaded.countDown()
        }
    }
}
