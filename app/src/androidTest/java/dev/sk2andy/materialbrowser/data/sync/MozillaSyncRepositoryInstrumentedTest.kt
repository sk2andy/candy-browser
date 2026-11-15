package dev.sk2andy.materialbrowser.data.sync

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.sync.MozillaSyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MozillaSyncRepositoryInstrumentedTest {
    @Test
    fun configuredSdkCreatesOAuthUrlAndClearsBindingOnLogout() = runBlocking {
        assumeTrue(!BuildConfig.SYSTEM_WEBVIEW_ONLY)
        val repository = MozillaSyncRepository.getInstance(ApplicationProvider.getApplicationContext())
        withContext(Dispatchers.Main) {
            assertTrue(repository.configureLocalProfile("mozilla-smoke-test", "Candy smoke test"))
            try {
                repository.start()
                val uri = Uri.parse(requireNotNull(repository.beginAuthentication()))
                assertTrue(!uri.getQueryParameter("state").isNullOrBlank())
                assertTrue(!uri.getQueryParameter("code_challenge").isNullOrBlank())
                assertEquals("S256", uri.getQueryParameter("code_challenge_method"))
                assertEquals("a2270f727f45f648", uri.getQueryParameter("client_id"))
                assertEquals("oauth_webchannel_v1", uri.getQueryParameter("context"))
                assertNull(uri.getQueryParameter("redirect_uri"))
                assertEquals("https", uri.scheme)
                assertEquals("accounts.firefox.com", uri.host)
                assertTrue(uri.getQueryParameter("scope").orEmpty().contains("https://identity.mozilla.com/apps/oldsync"))
                repository.authenticationCancelled()
                assertEquals(MozillaSyncStatus.Error, repository.currentState().status)
                assertTrue(repository.beginAuthentication() != null)
            } finally {
                repository.logout()
            }
            assertEquals(MozillaSyncStatus.SignedOut, repository.currentState().status)
            assertNull(repository.currentState().localProfileId)
            assertNull(repository.currentState().currentDeviceId)
            assertTrue(repository.currentState().devices.isEmpty())
        }
    }
}
