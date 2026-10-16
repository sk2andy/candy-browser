package dev.sk2andy.materialbrowser.browser.guest

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import dev.sk2andy.materialbrowser.R

/** System authentication stays outside the guest page and cannot be supplied by an intent. */
internal class GuestBubbleAuthenticator(private val activity: AppCompatActivity) {
    private var pending = false

    fun authenticate(onResult: (Boolean) -> Unit) {
        if (pending) return
        if (!isAvailable(activity)) {
            onResult(false)
            return
        }
        pending = true
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    pending = false
                    onResult(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    pending = false
                    onResult(false)
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(R.string.guest_bubble_auth_title))
                .setSubtitle(activity.getString(R.string.guest_bubble_auth_subtitle))
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build(),
        )
    }

    companion object {
        private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

        fun isAvailable(context: Context): Boolean =
            BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }
}
