package com.example.applocker

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

class PinEntryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_APP_ID = "extra_app_id"
        const val EXTRA_APP_LABEL = "extra_app_label"
    }

    private lateinit var appId: String
    private lateinit var appLabel: String
    private lateinit var pinInput: EditText
    private lateinit var titleText: TextView
    private lateinit var errorText: TextView
    private lateinit var btnUseFingerprint: Button

    private var biometricPrompt: BiometricPrompt? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin_entry)

        val incomingAppId = intent.getStringExtra(EXTRA_APP_ID)
        if (incomingAppId == null) {
            // Nothing to guard against, bail out safely.
            LockState.isPromptShowing.set(false)
            finish()
            return
        }
        appId = incomingAppId
        appLabel = intent.getStringExtra(EXTRA_APP_LABEL) ?: getString(R.string.app_name)

        titleText = findViewById(R.id.textLockTitle)
        errorText = findViewById(R.id.textError)
        pinInput = findViewById(R.id.editPin)
        btnUseFingerprint = findViewById(R.id.btnUseFingerprint)
        val btnUnlock = findViewById<Button>(R.id.btnUnlock)
        val btnCancel = findViewById<Button>(R.id.btnCancel)

        titleText.text = getString(R.string.pin_entry_title, appLabel)

        btnUnlock.setOnClickListener { attemptPinUnlock() }

        btnCancel.setOnClickListener {
            LockState.isPromptShowing.set(false)
            moveTaskToBack(true)
            finish()
        }

        pinInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptPinUnlock()
                true
            } else {
                false
            }
        }

        val biometricAvailable = BiometricUtils.canAuthenticate(this)
        val biometricWanted = PrefsHelper.isBiometricEnabled(this)

        if (biometricAvailable && biometricWanted) {
            btnUseFingerprint.visibility = View.VISIBLE
            btnUseFingerprint.setOnClickListener { showBiometricPrompt() }
            setupBiometricPrompt()
            // Offer fingerprint immediately; user can still type the PIN underneath at any time.
            showBiometricPrompt()
        } else {
            btnUseFingerprint.visibility = View.GONE
        }
    }

    private fun setupBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    unlockSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // User cancelled, tapped "Use PIN instead", or too many failed attempts.
                    // The PIN field below remains available, so no extra handling is needed here.
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Fingerprint didn't match; the system dialog stays open for another attempt.
                }
            }
        )
    }

    private fun showBiometricPrompt() {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_prompt_title))
            .setSubtitle(getString(R.string.pin_entry_title, appLabel))
            .setNegativeButtonText(getString(R.string.use_pin_instead))
            .build()
        biometricPrompt?.authenticate(promptInfo)
    }

    private fun attemptPinUnlock() {
        val pin = pinInput.text.toString()
        if (pin.length != AppConstants.PIN_LENGTH) {
            showError(getString(R.string.error_pin_length))
            return
        }

        if (PrefsHelper.verifyPin(this, pin)) {
            unlockSuccess()
        } else {
            showError(getString(R.string.error_pin_wrong))
        }
    }

    private fun unlockSuccess() {
        val app = AppConstants.findById(appId)
        app?.let { LockState.markUnlocked(it.packageNames) }
        LockState.isPromptShowing.set(false)
        finish()
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
        pinInput.text.clear()
    }

    /**
     * Pressing back should not reveal the locked app underneath - send the
     * user home instead, same as tapping Cancel.
     */
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        LockState.isPromptShowing.set(false)
        moveTaskToBack(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        // In case the activity is destroyed without an explicit unlock/cancel
        // (e.g. system kills it), make sure we don't leave the flag stuck.
        LockState.isPromptShowing.set(false)
    }
}
