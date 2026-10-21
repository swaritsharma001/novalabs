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
    private const val POPPAY_PUBLIC_KEY = "pp_live_38a1b0d4c3b2bd709fe4ea699b1d574509cd"
    private const val DEFAULT_UPI_ID = "8986917820@fam"
    private const val MERCHANT_NAME = "NEXBOT INDIA"

    private val client = OkHttpClient()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    /**
     * Creates an order via NxtIndia Backend or direct PopPay Order API
     */
    fun createOrder(
        context: Context,
        userToken: String,
        amount: String = "100.00",
        callback: (Result<PopPayOrder>) -> Unit
    ) {
        val backendUrl = "${NxtIndiaApiClient.getBaseUrl(context)}/poppay/create-order"
        val json = JSONObject().apply {
            put("amount", amount)
        }

        val request = Request.Builder()
            .url(backendUrl)
            .addHeader("Authorization", userToken)
            .post(json.toString().toRequestBody(JSON_MEDIA))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Fallback direct order simulation if backend is offline
                val fallbackOrderId = "NXT_${System.currentTimeMillis()}"
                val fallback = PopPayOrder(
                    orderId = fallbackOrderId,
                    payableAmount = amount,
                    checkoutUrl = "$POPPAY_BASE_URL/pay?pa=$DEFAULT_UPI_ID&am=100&cu=INR&pn=${Uri.encode(MERCHANT_NAME)}",
                    upiDeepLink = "upi://pay?pa=$DEFAULT_UPI_ID&pn=${Uri.encode(MERCHANT_NAME)}&am=$amount&cu=INR&tn=$fallbackOrderId"
                )
                mainHandler.post { callback(Result.success(fallback)) }
            }

            override fun onResponse(call: Call, response: Response) {
                val resBody = response.body?.string().orEmpty()
                mainHandler.post {
                    try {
                        val jsonRes = JSONObject(resBody as String)
                        val orderId = jsonRes.optString("order_id", "NXT_${System.currentTimeMillis()}")
                        val payableAmount = jsonRes.optString("payableAmount", amount)
                        val checkoutUrl = jsonRes.optString(
                            "checkoutUrl",
                            "$POPPAY_BASE_URL/pay/$orderId"
                        )
                        val upiDeepLink = jsonRes.optString(
                            "upiDeepLink",
                            "upi://pay?pa=$DEFAULT_UPI_ID&pn=${Uri.encode(MERCHANT_NAME)}&am=$payableAmount&cu=INR&tn=$orderId"
                        )
                        callback(Result.success(PopPayOrder(orderId, payableAmount, checkoutUrl, upiDeepLink)))
                    } catch (e: Exception) {
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }

    /**
     * Launch native UPI intent (GPay, PhonePe, Paytm, FamApp, BHIM)
     */
    fun openUpiIntent(
        context: Context,
        upiUrl: String? = null,
        amount: String = "100",
        orderId: String = "NXT_" + System.currentTimeMillis()
    ): Boolean {
        return try {
            val finalUri = if (!upiUrl.isNullOrEmpty()) {
                Uri.parse(upiUrl)
            } else {
                Uri.parse(
                    "upi://pay?pa=$DEFAULT_UPI_ID&pn=${Uri.encode(MERCHANT_NAME)}&am=$amount&cu=INR&tn=${Uri.encode(orderId)}"
                )
            }
            val intent = Intent(Intent.ACTION_VIEW, finalUri)
            context.startActivity(Intent.createChooser(intent, "Pay via UPI App"))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Open PopPay Web Checkout (Hosted URL from docs)
     */
    fun openPopPayWebCheckout(
        context: Context,
        checkoutUrl: String? = null,
        orderId: String? = null
    ) {
        val targetUrl = when {
            !checkoutUrl.isNullOrEmpty() -> checkoutUrl
            !orderId.isNullOrEmpty() -> "$POPPAY_BASE_URL/pay/$orderId"
            else -> "$POPPAY_BASE_URL/pay?pa=$DEFAULT_UPI_ID&am=100&cu=INR&pn=${Uri.encode(MERCHANT_NAME)}"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
        context.startActivity(intent)
    }

    /**
     * Check order status according to Section 8 of PopPay docs:
     * GET https://poppay.tech/api/v1/checkout/:orderId/status
     */
    fun pollOrderStatus(
        orderId: String,
        callback: (Result<PopPayStatus>) -> Unit
    ) {
        val request = Request.Builder()
            .url("$POPPAY_BASE_URL/api/v1/checkout/$orderId/status")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { callback(Result.failure(e)) }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string().orEmpty()
                    val json = JSONObject(body as String)
                    val status = json.optString("status", "PENDING")
                    val utr = json.optString("utr", "")
                    val signature = json.optString("signature", "")
                    mainHandler.post {
                        callback(Result.success(PopPayStatus(status, utr, signature)))
                    }
                } catch (e: Exception) {
                    mainHandler.post { callback(Result.failure(e)) }
                }
            }
        })
    }
}

data class PopPayOrder(
    val orderId: String,
    val payableAmount: String,
    val checkoutUrl: String,
    val upiDeepLink: String
)

data class PopPayStatus(
    val status: String,
    val utr: String,
    val signature: String
)
