package com.novalabs.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class NxtBot(
    val id: String,
    val username: String,
    val avatar: String? = null,
    val isRunning: Boolean = false,
    val isBanned: Boolean = false,
    val ramUsage: String? = null,
    val cpuUsage: String? = null,
    val latency: String? = null,
    val uptime: String? = null
)

object NxtIndiaApiClient {

    private const val PREFS_NAME = "nxt_india_api_prefs"
    private const val KEY_BASE_URL = "api_base_url"
    private const val DEFAULT_BASE_URL = "http://10.0.2.2:5000"

    private val client = OkHttpClient()
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun getBaseUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
    }

    fun setBaseUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BASE_URL, url.trimEnd('/')).apply()
    }

    /**
     * Submit UTR Reference Number (PricingTab.tsx /core/utr)
     */
    fun submitUtr(context: Context, userToken: String, utr: String, callback: (Result<String>) -> Unit) {
        val url = "${getBaseUrl(context)}/core/utr"
        val json = JSONObject().apply {
            put("utr", utr)
        }
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", userToken)
            .post(json.toString().toRequestBody(JSON_MEDIA))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { callback(Result.failure(e)) }
            }

            override fun onResponse(call: Call, response: Response) {
                val resBody = response.body?.string().orEmpty()
                mainHandler.post {
                    if (response.isSuccessful) {
                        callback(Result.success("UTR submitted successfully!"))
                    } else {
                        val errMsg = try {
                            val obj = JSONObject(resBody)
                            obj.optString("message", obj.optString("error", "Failed to submit UTR"))
                        } catch (e: Exception) {
                            "Failed to submit UTR: HTTP ${response.code}"
                        }
                        callback(Result.failure(Exception(errMsg)))
                    }
                }
            }
        })
    }

    fun fetchBots(context: Context, userToken: String, callback: (Result<List<NxtBot>>) -> Unit) {
        val url = "${getBaseUrl(context)}/core/bots"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", userToken)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post {
                    callback(Result.failure(e))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                mainHandler.post {
                    if (response.isSuccessful) {
                        try {
                            val botsList = mutableListOf<NxtBot>()
                            val array = JSONArray(body)
                            for (i in 0 until array.length()) {
                                val item = array.getJSONObject(i)
                                botsList.add(
                                    NxtBot(
                                        id = item.optString("id", "$i"),
                                        username = item.optString("username", "Discord Bot #$i"),
                                        avatar = item.optString("avatar", null),
                                        isRunning = item.optBoolean("isRunning", false),
                                        uptime = item.optString("uptime", null)
                                    )
                                )
                            }
                            callback(Result.success(botsList))
                        } catch (e: Exception) {
                            callback(Result.failure(e))
                        }
                    } else {
                        callback(Result.failure(Exception("Failed to fetch bots: HTTP ${response.code}")))
                    }
                }
            }
        })
    }

    fun startBot(context: Context, userToken: String, botId: String, callback: (Result<Boolean>) -> Unit) {
        postBotAction(context, userToken, "start", botId, callback)
    }

    fun stopBot(context: Context, userToken: String, botId: String, callback: (Result<Boolean>) -> Unit) {
        postBotAction(context, userToken, "stop", botId, callback)
    }

    fun deleteBot(context: Context, userToken: String, botId: String, callback: (Result<Boolean>) -> Unit) {
        postBotAction(context, userToken, "delete", botId, callback)
    }

    private fun postBotAction(context: Context, userToken: String, action: String, botId: String, callback: (Result<Boolean>) -> Unit) {
        val url = "${getBaseUrl(context)}/core/$action"
        val json = JSONObject().apply {
            put("botId", botId)
        }
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", userToken)
            .post(json.toString().toRequestBody(JSON_MEDIA))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { callback(Result.failure(e)) }
            }

            override fun onResponse(call: Call, response: Response) {
                mainHandler.post {
                    if (response.isSuccessful) {
                        callback(Result.success(true))
                    } else {
                        callback(Result.failure(Exception("Action $action failed: HTTP ${response.code}")))
                    }
                }
            }
        })
    }

    fun addBotToken(context: Context, userToken: String, botToken: String, callback: (Result<Boolean>) -> Unit) {
        val url = "${getBaseUrl(context)}/core/token"
        val json = JSONObject().apply {
            put("token", botToken)
        }
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", userToken)
            .post(json.toString().toRequestBody(JSON_MEDIA))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { callback(Result.failure(e)) }
            }

            override fun onResponse(call: Call, response: Response) {
                mainHandler.post {
                    if (response.isSuccessful) {
                        callback(Result.success(true))
                    } else {
                        callback(Result.failure(Exception("Add bot token failed: HTTP ${response.code}")))
                    }
                }
            }
        })
    }
}
