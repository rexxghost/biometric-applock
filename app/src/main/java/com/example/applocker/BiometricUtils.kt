package com.example.applocker

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators

/**
 * Wraps the BiometricManager check so the rest of the app doesn't need to
 * know about the Authenticators bitmask.
 */
object BiometricUtils {

    private const val ALLOWED_AUTHENTICATORS =
        Authenticators.BIOMETRIC_STRONG or Authenticators.BIOMETRIC_WEAK

    /** True if the device has a usable fingerprint/face sensor with at least one enrolled credential. */
    fun canAuthenticate(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(ALLOWED_AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }
}
