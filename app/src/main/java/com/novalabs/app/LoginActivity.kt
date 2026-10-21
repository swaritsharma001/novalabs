package com.novalabs.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Base64
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class LoginActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "novalabs_auth_prefs"
        private const val KEY_STORE_AUTHED_TOKEN = "STORE_AUTHED_TOKEN"
        private const val KEY_STORE_AUTHED_USERNAME = "STORE_AUTHED_USERNAME"
        private const val KEY_STORE_AUTHED_USER_ID = "STORE_AUTHED_USER_ID"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bluecord persistent token check: auto-login if token is already saved
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedToken = prefs.getString(KEY_STORE_AUTHED_TOKEN, null)
        if (!savedToken.isNullOrEmpty()) {
            val username = prefs.getString(KEY_STORE_AUTHED_USERNAME, "Nova User") ?: "Nova User"
            val userId = prefs.getString(KEY_STORE_AUTHED_USER_ID, "10839201948102") ?: "10839201948102"
            
            // Check for background Bluecord updates
            checkBluecordUpdates()
            navigateToDashboard(username, userId)
            return
        }

        setContentView(R.layout.activity_login)

        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<MaterialButton>(R.id.btnLogin)
        val btnTokenLogin = findViewById<MaterialButton>(R.id.btnTokenLogin)

        // Standard Discord credential login with Captcha & MFA support
        btnLogin.setOnClickListener {
            val email = etEmail.text?.toString()?.trim() ?: ""
            val password = etPassword.text?.toString() ?: ""

            if (email.isEmpty()) {
                etEmail.error = "Please enter your email"
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                etPassword.error = "Please enter your password"
                return@setOnClickListener
            }

            performCredentialLogin(email, password, null, null)
        }

        // Bluecord direct Token Login flow
        btnTokenLogin.setOnClickListener {
            showTokenLoginDialog()
        }
    }

    /**
     * Executes Discord login and handles Captcha challenges via CaptchaResolverDialog
     */
    private fun performCredentialLogin(
        email: String,
        pass: String,
        captchaKey: String?,
        captchaRqToken: String?
    ) {
        Toast.makeText(this, "Signing in...", Toast.LENGTH_SHORT).show()

        DiscordAuthService.login(email, pass, captchaKey, captchaRqToken) { result ->
            when (result) {
                is DiscordAuthService.LoginResult.Success -> {
                    Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                    saveSessionAndProceed(result.token, result.username, result.userId)
                }

                is DiscordAuthService.LoginResult.CaptchaRequired -> {
                    Toast.makeText(this, "Captcha challenge required", Toast.LENGTH_SHORT).show()
                    CaptchaResolverDialog.show(
                        context = this,
                        siteKey = result.siteKey,
                        rqData = result.rqData,
                        callback = object : CaptchaResolverDialog.CaptchaCallback {
                            override fun onResolved(captchaKey: String) {
                                // Re-attempt login passing the resolved captcha token and rqtoken
                                performCredentialLogin(email, pass, captchaKey, result.rqToken)
                            }

                            override fun onCancelled() {
                                Toast.makeText(this@LoginActivity, "Captcha verification cancelled", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                is DiscordAuthService.LoginResult.MfaRequired -> {
                    promptMfaCode(result.ticket)
                }

                is DiscordAuthService.LoginResult.Error -> {
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Prompts for two-factor authentication / TOTP code if enabled on the Discord account
     */
    private fun promptMfaCode(ticket: String) {
        val input = EditText(this).apply {
            hint = "6-digit authentication code"
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
        }

        val container = FrameLayout(this).apply {
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(48, 24, 48, 16) }
            layoutParams = params
            addView(input)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Two-Factor Authentication")
            .setMessage("Enter the 6-digit code from your authenticator app:")
            .setView(container)
            .setPositiveButton("Verify") { _, _ ->
                val code = input.text.toString().trim()
                if (code.isNotEmpty()) {
                    DiscordAuthService.submitMfaCode(ticket, code) { result ->
                        if (result is DiscordAuthService.LoginResult.Success) {
                            Toast.makeText(this, "Authentication verified!", Toast.LENGTH_SHORT).show()
                            saveSessionAndProceed(result.token, result.username, result.userId)
                        } else if (result is DiscordAuthService.LoginResult.Error) {
                            Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Implements Bluecord's LoginPageOptions Token Login Dialog
     */
    private fun showTokenLoginDialog() {
        val input = EditText(this).apply {
            hint = "Enter your token..."
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1E1F22"))
            setPadding(40, 32, 40, 32)
        }

        val container = FrameLayout(this).apply {
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(48, 24, 48, 16) }
            layoutParams = params
            addView(input)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Token Login")
            .setView(container)
            .setNeutralButton("Help") { _, _ ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Token Login")
                    .setMessage(
                        "Use this if you are logged in on your PC or have your token on hand. " +
                        "If you're not sure what a Discord token is, don't worry, it's not very important.\n\n" +
                        "Your token can be found on your PC browser by looking at the Authorization request headers.\n\n" +
                        "NOTE: No one can see your token but you and Discord, and DO NOT SCREENSHOT, SHOW OR SHARE YOUR TOKEN!"
                    )
                    .setPositiveButton("OK", null)
                    .show()
            }
            .setNegativeButton("Dismiss", null)
            .setPositiveButton("Submit") { _, _ ->
                val rawToken = input.text.toString().trim()
                val cleanToken = rawToken.replace(Regex("[^A-Za-z0-9\\-_\\.\\+]"), "")

                if (cleanToken.isEmpty()) {
                    Toast.makeText(this, "Token is empty or invalid", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                verifyAndAuthenticateToken(cleanToken)
            }
            .show()
    }

    /**
     * Replicates Bluecord's TokenChecker verification pipeline
     */
    private fun verifyAndAuthenticateToken(token: String) {
        val mainHandler = Handler(Looper.getMainLooper())
        Toast.makeText(this, "Checking if token is valid...", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val url = URL("https://discord.com/api/v9/users/@me")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", token)
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:126.0)")
                    connectTimeout = 7000
                    readTimeout = 7000
                }

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(responseBody)
                    val username = json.optString("global_name", json.optString("username", "Nova User"))
                    val userId = json.optString("id", "10839201948102")

                    mainHandler.post {
                        Toast.makeText(this, "Token login was successful", Toast.LENGTH_SHORT).show()
                        saveSessionAndProceed(token, username, userId)
                    }
                } else if (responseCode == 401 || responseCode == 403) {
                    mainHandler.post {
                        Toast.makeText(this, "Token is either invalid or revoked by Discord", Toast.LENGTH_LONG).show()
                    }
                } else {
                    fallbackFormatValidation(mainHandler, token)
                }
            } catch (e: Exception) {
                fallbackFormatValidation(mainHandler, token)
            }
        }.start()
    }

    private fun fallbackFormatValidation(handler: Handler, token: String) {
        var resolvedUserId: String? = null
        val parts = token.split(".")
        if (parts.isNotEmpty()) {
            try {
                val decoded = String(Base64.decode(parts[0], Base64.DEFAULT), Charsets.UTF_8).trim()
                if (decoded.isNotEmpty() && decoded.all { it.isDigit() }) {
                    resolvedUserId = decoded
                }
            } catch (_: Exception) {}
        }

        if (resolvedUserId != null) {
            val username = "User_${resolvedUserId.takeLast(4)}"
            handler.post {
                Toast.makeText(this, "Token login was successful", Toast.LENGTH_SHORT).show()
                saveSessionAndProceed(token, username, resolvedUserId)
            }
        } else {
            handler.post {
                Toast.makeText(this, "Not connected to the Internet or token is invalid", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveSessionAndProceed(token: String, username: String, userId: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STORE_AUTHED_TOKEN, token)
            .putString(KEY_STORE_AUTHED_USERNAME, username)
            .putString(KEY_STORE_AUTHED_USER_ID, userId)
            .apply()

        // Report telemetry to Bluecord API
        BluecordApiClient.sendEvent("LOGIN", "User $userId ($username) authenticated")

        // Trigger Bluecord update check
        checkBluecordUpdates()

        navigateToDashboard(username, userId)
    }

    private fun checkBluecordUpdates() {
        BluecordApiClient.checkUpdates { update ->
            if (update != null && update.hasUpdate) {
                Toast.makeText(this, "Bluecord Update Available: ${update.updateMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun navigateToDashboard(username: String, userId: String) {
        val intent = Intent(this, NovaLabsDashboardActivity::class.java).apply {
            putExtra("EXTRA_USER_NAME", username)
            putExtra("EXTRA_USER_ID", userId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
