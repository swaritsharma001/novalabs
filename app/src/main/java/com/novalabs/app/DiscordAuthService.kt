package com.novalabs.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

object DiscordAuthService {

    private const val EXPERIMENTS_ENDPOINT = "https://discord.com/api/v9/experiments"
    private const val LOGIN_ENDPOINT = "https://discord.com/api/v9/auth/login"
    private const val TOTP_ENDPOINT = "https://discord.com/api/v9/auth/mfa/totp"
    private const val USER_ME_ENDPOINT = "https://discord.com/api/v9/users/@me"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var cachedFingerprint: String? = null

    sealed class LoginResult {
        data class Success(val token: String, val userId: String, val username: String) : LoginResult()
        data class CaptchaRequired(val siteKey: String, val rqToken: String?, val rqData: String?) : LoginResult()
        data class MfaRequired(val ticket: String) : LoginResult()
        data class Error(val message: String) : LoginResult()
    }

    /**
     * Executes Discord credential login matching Bluecord's exact headers and session fingerprint
     */
    fun login(
        context: Context,
        email: String,
        pass: String,
        captchaKey: String? = null,
        captchaRqToken: String? = null,
        callback: (LoginResult) -> Unit
    ) {
        Thread {
            try {
                // Step 1: Ensure session has a valid pre-flight fingerprint from /experiments
                if (cachedFingerprint.isNullOrEmpty()) {
                    cachedFingerprint = fetchExperimentsFingerprint(context)
                }

                val url = URL(LOGIN_ENDPOINT)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    
                    // Match Bluecord's exact header dictionary from ReactNativeSpoof
                    val headers = ReactNativeSpoof.makeHeaderMap(
                        context = context,
                        authToken = null,
                        fingerprint = cachedFingerprint
                    )
                    headers.forEach { (k, v) ->
                        setRequestProperty(k, v)
                    }

                    // Attach captcha headers if retrying after captcha challenge
                    if (!captchaKey.isNullOrEmpty()) {
                        setRequestProperty("x-captcha-key", captchaKey)
                    }
                    if (!captchaRqToken.isNullOrEmpty()) {
                        setRequestProperty("x-captcha-rqtoken", captchaRqToken)
                    }

                    connectTimeout = 12000
                    readTimeout = 12000
                    doOutput = true
                }

                val payload = JSONObject().apply {
                    put("login", email)
                    put("password", pass)
                    put("undelete", false)
                    put("login_source", JSONObject.NULL)
                    put("gift_code_sku_id", JSONObject.NULL)
                    if (!captchaKey.isNullOrEmpty()) {
                        put("captcha_key", captchaKey)
                    }
                    if (!captchaRqToken.isNullOrEmpty()) {
                        put("captcha_rqtoken", captchaRqToken)
                    }
                }

                conn.outputStream.use {
                    it.write(payload.toString().toByteArray(StandardCharsets.UTF_8))
                }

                val responseCode = conn.responseCode
                val stream: InputStream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                val responseText = stream.bufferedReader().use { it.readText() }
                val json = try { JSONObject(responseText) } catch (e: Exception) { JSONObject() }

                if (responseCode == 200) {
                    if (json.has("token")) {
                        val token = json.getString("token")
                        val userId = json.optString("user_id", "")
                        fetchUserInfo(context, token, userId) { result ->
                            mainHandler.post { callback(result) }
                        }
                        return@Thread
                    } else if (json.optBoolean("mfa", false) && json.has("ticket")) {
                        val ticket = json.getString("ticket")
                        mainHandler.post { callback(LoginResult.MfaRequired(ticket)) }
                        return@Thread
                    }
                }

                // Check for Captcha challenge
                if (json.has("captcha_sitekey")) {
                    val siteKey = json.getString("captcha_sitekey")
                    val rqToken = json.optString("captcha_rqtoken", null)
                    val rqData = json.optString("captcha_rqdata", null)
                    mainHandler.post { callback(LoginResult.CaptchaRequired(siteKey, rqToken, rqData)) }
                    return@Thread
                }

                // Error message parsing
                val errorMessage = when {
                    json.has("message") -> json.getString("message")
                    json.has("email") -> json.getJSONArray("email").join(", ")
                    json.has("password") -> json.getJSONArray("password").join(", ")
                    json.has("login") -> json.getJSONArray("login").join(", ")
                    else -> "Login failed (HTTP $responseCode)"
                }

                mainHandler.post { callback(LoginResult.Error(errorMessage)) }

            } catch (e: Exception) {
                mainHandler.post { callback(LoginResult.Error("Network error: ${e.localizedMessage ?: "Connection timed out"}")) }
            }
        }.start()
    }

    /**
     * Resolves 2FA / MFA TOTP verification using Bluecord headers
     */
    fun submitMfaCode(context: Context, ticket: String, code: String, callback: (LoginResult) -> Unit) {
        Thread {
            try {
                val url = URL(TOTP_ENDPOINT)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    
                    val headers = ReactNativeSpoof.makeHeaderMap(
                        context = context,
                        authToken = null,
                        fingerprint = cachedFingerprint
                    )
                    headers.forEach { (k, v) ->
                        setRequestProperty(k, v)
                    }

                    connectTimeout = 8000
                    readTimeout = 8000
                    doOutput = true
                }

                val payload = JSONObject().apply {
                    put("ticket", ticket)
                    put("code", code)
                    put("login_source", JSONObject.NULL)
                }

                conn.outputStream.use {
                    it.write(payload.toString().toByteArray(StandardCharsets.UTF_8))
                }

                val responseCode = conn.responseCode
                val stream: InputStream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                val responseText = stream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)

                if (responseCode == 200 && json.has("token")) {
                    val token = json.getString("token")
                    val userId = json.optString("user_id", "")
                    fetchUserInfo(context, token, userId) { result ->
                        mainHandler.post { callback(result) }
                    }
                } else {
                    val msg = json.optString("message", "Invalid 2FA code")
                    mainHandler.post { callback(LoginResult.Error(msg)) }
                }
            } catch (e: Exception) {
                mainHandler.post { callback(LoginResult.Error("MFA error: ${e.localizedMessage}")) }
            }
        }.start()
    }

    /**
     * Bluecord login initiation: fetches initial fingerprint from Discord experiments
     */
    private fun fetchExperimentsFingerprint(context: Context): String? {
        return try {
            val url = URL(EXPERIMENTS_ENDPOINT)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                val headers = ReactNativeSpoof.makeHeaderMap(context, null, null)
                headers.forEach { (k, v) ->
                    setRequestProperty(k, v)
                }
                connectTimeout = 6000
                readTimeout = 6000
            }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                json.optString("fingerprint", null)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Retrieves user profile details using the newly acquired authorization token
     */
    private fun fetchUserInfo(context: Context, token: String, fallbackUserId: String, onDone: (LoginResult) -> Unit) {
        try {
            val url = URL(USER_ME_ENDPOINT)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                val headers = ReactNativeSpoof.makeHeaderMap(context, token, cachedFingerprint)
                headers.forEach { (k, v) ->
                    setRequestProperty(k, v)
                }
                connectTimeout = 6000
                readTimeout = 6000
            }

            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val userJson = JSONObject(body)
                val username = userJson.optString("global_name", userJson.optString("username", "Nova User"))
                val userId = userJson.optString("id", fallbackUserId)
                onDone(LoginResult.Success(token, userId, username))
                return
            }
        } catch (_: Exception) {}

        // Fallback to snowflake decode if user/me lookup times out
        var resolvedUserId = fallbackUserId
        if (resolvedUserId.isEmpty()) {
            val prefix = token.substringBefore(".")
            try {
                resolvedUserId = String(Base64.decode(prefix, Base64.DEFAULT), StandardCharsets.UTF_8).trim()
            } catch (_: Exception) {}
        }
        if (resolvedUserId.isEmpty()) resolvedUserId = "10839201948102"
        onDone(LoginResult.Success(token, resolvedUserId, "User_${resolvedUserId.takeLast(4)}"))
    }
}
