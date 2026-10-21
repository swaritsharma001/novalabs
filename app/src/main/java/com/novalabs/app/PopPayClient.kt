package com.novalabs.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

object PopPayClient {

    private const val POPPAY_BASE_URL = "https://poppay.tech"
    private const val POPPAY_SECRET_KEY = "pp_secret_ee56fe64f89f5f2f2c2940749e489d8119e8baab27692b28f1b2872cfbe8c4e4"

    private val client = OkHttpClient()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    /**
     * Creates real PopPay order on poppay.tech/api/v1/orders
     */
    fun createRealOrder(
        amount: String = "100.00",
        receipt: String = "rcpt_${System.currentTimeMillis()}",
        callback: (Result<PopPayOrder>) -> Unit
    ) {
        val json = JSONObject().apply {
            put("amount", amount)
            put("receipt", receipt)
        }

        val request = Request.Builder()
            .url("$POPPAY_BASE_URL/api/v1/orders")
            .addHeader("Authorization", "Bearer $POPPAY_SECRET_KEY")
            .addHeader("Content-Type", "application/json")
            .post(json.toString().toRequestBody(JSON_MEDIA))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { callback(Result.failure(e)) }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                mainHandler.post {
                    if (response.isSuccessful) {
                        try {
                            val data = JSONObject(body as String)
                            val orderId = data.getString("order_id")
                            val payableAmount = data.optString("payableAmount", amount)
                            val relativeCheckoutUrl = data.optString("checkoutUrl", "/pay/$orderId")
                            val fullCheckoutUrl = if (relativeCheckoutUrl.startsWith("http")) {
                                relativeCheckoutUrl
                            } else {
                                "$POPPAY_BASE_URL$relativeCheckoutUrl"
                            }
                            callback(Result.success(PopPayOrder(orderId, payableAmount, fullCheckoutUrl)))
                        } catch (e: Exception) {
                            callback(Result.failure(e))
                        }
                    } else {
                        callback(Result.failure(Exception("Order creation failed: HTTP ${response.code} - $body")))
                    }
                }
            }
        })
    }

    /**
     * Opens official normal PopPay checkout page: https://poppay.tech/pay/:orderId
     */
    fun openNormalCheckoutPage(context: Context, checkoutUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(checkoutUrl))
        context.startActivity(intent)
    }

    /**
     * Periodically poll order status: GET https://poppay.tech/api/v1/checkout/:orderId/status
     */
    fun startStatusPolling(
        orderId: String,
        onPaid: (utr: String, signature: String) -> Unit,
        onStatus: ((status: String) -> Unit)? = null
    ): () -> Unit {
        var isPolling = true
        lateinit var pollRunnable: Runnable

        pollRunnable = Runnable {
            if (!isPolling) return@Runnable

            val request = Request.Builder()
                .url("$POPPAY_BASE_URL/api/v1/checkout/$orderId/status")
                .get()
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (isPolling) mainHandler.postDelayed(pollRunnable, 3000)
                }

                override fun onResponse(call: Call, response: Response) {
                    val body = response.body?.string().orEmpty()
                    try {
                        val json = JSONObject(body as String)
                        val status = json.optString("status", "PENDING")
                        mainHandler.post { onStatus?.invoke(status) }

                        if (status.equals("PAID", ignoreCase = true)) {
                            isPolling = false
                            val utr = json.optString("utr", "")
                            val signature = json.optString("signature", "")
                            mainHandler.post { onPaid(utr, signature) }
                            return
                        }
                    } catch (e: Exception) {
                        // ignore and retry
                    }

                    if (isPolling) {
                        mainHandler.postDelayed(pollRunnable, 3000)
                    }
                }
            })
        }

        mainHandler.postDelayed(pollRunnable, 2000)

        // Returns stop function
        return { isPolling = false }
    }
}

data class PopPayOrder(
    val orderId: String,
    val payableAmount: String,
    val checkoutUrl: String
)
