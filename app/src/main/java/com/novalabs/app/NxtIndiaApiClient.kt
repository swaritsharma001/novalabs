package com.novalabs.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class NxtBot(
    val id: String,
    val username: String,
    val isRunning: Boolean,
    val isBanned: Boolean,
    val lastActive: String?,
    val ramUsage: String?,
    val cpuUsage: String?,
    val latency: String?
)

object NxtIndiaApiClient {

    private const val PREFS_NAME = "nxt_api_config"
    private const val KEY_CUSTOM_URL = "custom_backend_url"
    
    // Default URL pointing to local server or deployed instance
    private const val DEFAULT_BACKEND_URL = "http://10.0.2.2:5000"

    private val mainHandler = Handler(Looper.getMainLooper())

    fun getBaseUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CUSTOM_URL, DEFAULT_BACKEND_URL) ?: DEFAULT_BACKEND_URL
    }

    fun setBaseUrl(context: Context, url: String) {
        val trimmed = url.trim().removeSuffix("/")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_URL, trimmed)
            .apply()
    }

    fun fetchUserProfile(context: Context, token: String, callback: (Result<JSONObject>) -> Unit) {
        Thread {
            try {
                val url = URL("${getBaseUrl(context)}/auth/user")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", token)
                    connectTimeout = 6000
                    readTimeout = 6000
                }

                val responseCode = conn.responseCode
                val stream: InputStream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                val body = stream.bufferedReader().use { it.readText() }

                if (responseCode == 200) {
                    val json = JSONObject(body)
                    mainHandler.post { callback(Result.success(json)) }
                } else {
                    mainHandler.post { callback(Result.failure(Exception("HTTP $responseCode: $body"))) }
                }
            } catch (e: Exception) {
                mainHandler.post { callback(Result.failure(e)) }
            }
        }.start()
    }

    fun fetchBots(context: Context, token: String, callback: (Result<List<NxtBot>>) -> Unit) {
        Thread {
            try {
                val url = URL("${getBaseUrl(context)}/core/bots")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", token)
                    connectTimeout = 8000
                    readTimeout = 8000
                }

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val array = JSONArray(body)
                    val bots = mutableListOf<NxtBot>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        bots.add(
                            NxtBot(
                                id = obj.getString("_id"),
                                username = obj.optString("username", "Unknown Bot"),
                                isRunning = obj.optBoolean("isRunning", false),
                                isBanned = obj.optBoolean("isBanned", false),
                                lastActive = obj.optString("lastActive", null),
                                ramUsage = obj.optString("ramUsage", "0 MB"),
                                cpuUsage = obj.optString("cpuUsage", "0%"),
                                latency = obj.optString("latency", null)
                            )
                        )
                    }
                    mainHandler.post { callback(Result.success(bots)) }
                } else {
                    val errorStream = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Error"
                    mainHandler.post { callback(Result.failure(Exception(errorStream))) }
                }
            } catch (e: Exception) {
                mainHandler.post { callback(Result.failure(e)) }
            }
        }.start()
    }

    fun startBot(context: Context, token: String, botId: String, callback: (Result<String>) -> Unit) {
        postJson(context, "/core/start", token, JSONObject().apply { put("id", botId) }, callback)
    }

    fun stopBot(context: Context, token: String, botId: String, callback: (Result<String>) -> Unit) {
        postJson(context, "/core/stop", token, JSONObject().apply { put("id", botId) }, callback)
    }

    fun addBotToken(context: Context, token: String, botToken: String, callback: (Result<String>) -> Unit) {
        postJson(context, "/core/token", token, JSONObject().apply { put("token", botToken) }, callback)
    }

    fun deleteBot(context: Context, token: String, botId: String, callback: (Result<String>) -> Unit) {
        postJson(context, "/core/delete", token, JSONObject().apply { put("id", botId) }, callback)
    }

    private fun postJson(
        context: Context,
        endpoint: String,
        token: String,
        payload: JSONObject,
        callback: (Result<String>) -> Unit
    ) {
        Thread {
            try {
                val url = URL("${getBaseUrl(context)}$endpoint")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", token)
                    connectTimeout = 8000
                    readTimeout = 8000
                    doOutput = true
                }

                conn.outputStream.use {
                    it.write(payload.toString().toByteArray(StandardCharsets.UTF_8))
                }

                val responseCode = conn.responseCode
                val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() } ?: ""

                if (responseCode in 200..299) {
                    val msg = try { JSONObject(body).optString("message", "Success") } catch (_: Exception) { body }
                    mainHandler.post { callback(Result.success(msg)) }
                } else {
                    val msg = try { JSONObject(body).optString("message", "Failed: HTTP $responseCode") } catch (_: Exception) { "Failed: HTTP $responseCode" }
                    mainHandler.post { callback(Result.failure(Exception(msg))) }
                }
            } catch (e: Exception) {
                mainHandler.post { callback(Result.failure(e)) }
            }
        }.start()
    }
}
