package com.novalabs.app

import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

object BluecordApiClient {

    private const val BASE_URL = "https://bluesmods.com"
    private const val CLIENT_VERSION = "v126.21"
    private val mainHandler = Handler(Looper.getMainLooper())

    data class UpdateResult(
        val hasUpdate: Boolean,
        val newVersionCode: Long,
        val updateMessage: String,
        val downloadUrl: String
    )

    /**
     * Polls the Bluecord server for updates and remote configuration
     * Mirroring Bluecord's Xiphias / Poll endpoint
     */
    fun checkUpdates(callback: (UpdateResult?) -> Unit) {
        Thread {
            try {
                val url = URL("$BASE_URL/bluecord/api/v1/xiphias")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("X-Package-Name", "com.novalabs.app")
                    setRequestProperty("X-Version-Code", "126021")
                    setRequestProperty("User-Agent", "Bluecord/$CLIENT_VERSION (Android)")
                    connectTimeout = 6000
                    readTimeout = 6000
                }

                if (connection.responseCode == 200) {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val updateInfo = json.optJSONObject("update_info")
                    if (updateInfo != null && updateInfo.optInt("action", 0) == 1) {
                        val result = UpdateResult(
                            hasUpdate = true,
                            newVersionCode = updateInfo.optLong("new_version_code", 0),
                            updateMessage = updateInfo.optString("update_message", "A new update is available"),
                            downloadUrl = updateInfo.optString("file_url", "https://bluesmods.com/bluecord")
                        )
                        mainHandler.post { callback(result) }
                        return@Thread
                    }
                }
                mainHandler.post { callback(null) }
            } catch (e: Exception) {
                mainHandler.post { callback(null) }
            }
        }.start()
    }

    /**
     * Submits client telemetry/crash events to Bluecord's EventSink endpoint
     */
    fun sendEvent(eventType: String, details: String) {
        Thread {
            try {
                val url = URL("$BASE_URL/bluecord/api/v1/events")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("User-Agent", "Bluecord/$CLIENT_VERSION (Android)")
                    doOutput = true
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                val payload = JSONObject().apply {
                    put("event_type", eventType)
                    put("timestamp", System.currentTimeMillis())
                    put("client", "NovaLabs-Bluecord")
                    put("details", details)
                }

                connection.outputStream.use {
                    it.write(payload.toString().toByteArray(StandardCharsets.UTF_8))
                }

                connection.responseCode // execute
            } catch (_: Exception) {}
        }.start()
    }
}
