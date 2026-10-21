package com.novalabs.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

object PopPayClient {

    private const val POPPAY_BASE_URL = "https://poppay.tech"
    private const val DEFAULT_UPI_ID = "8986917820@fam"
    private const val DEFAULT_AMOUNT = "100.00"
    private const val MERCHANT_NAME = "NEXBOT INDIA"

    private val client = OkHttpClient()
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Launch native UPI intent (GPay, PhonePe, Paytm, FamApp, BHIM)
     */
    fun openUpiIntent(
        context: Context,
        amount: String = "100",
        orderId: String = "NXT_" + System.currentTimeMillis()
    ): Boolean {
        return try {
            val uri = Uri.parse(
                "upi://pay?pa=$DEFAULT_UPI_ID&pn=${Uri.encode(MERCHANT_NAME)}&am=$amount&cu=INR&tn=${Uri.encode("Upgrade $orderId")}"
            )
            val intent = Intent(Intent.ACTION_VIEW, uri)
            context.startActivity(Intent.createChooser(intent, "Pay via UPI App"))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Open PopPay Web Checkout (Option B / Option A from docs)
     */
    fun openPopPayWebCheckout(
        context: Context,
        orderId: String? = null
    ) {
        val checkoutUrl = if (!orderId.isNullOrEmpty()) {
            "$POPPAY_BASE_URL/pay/$orderId"
        } else {
            // Direct payment gateway checkout portal
            "$POPPAY_BASE_URL/pay?pa=$DEFAULT_UPI_ID&am=100&cu=INR&pn=${Uri.encode(MERCHANT_NAME)}"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(checkoutUrl))
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

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                mainHandler.post { callback(Result.failure(e)) }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
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

data class PopPayStatus(
    val status: String,
    val utr: String,
    val signature: String
)
