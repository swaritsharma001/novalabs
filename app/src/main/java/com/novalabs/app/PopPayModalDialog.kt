package com.novalabs.app

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import org.json.JSONObject

object PopPayModalDialog {

    interface PopPayCallback {
        fun onPaymentSuccess(orderId: String, paymentId: String, utr: String, signature: String)
        fun onDismiss()
    }

    fun show(
        context: Context,
        orderId: String,
        amount: String,
        checkoutUrl: String,
        callback: PopPayCallback
    ) {
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_poppay_modal, null)
        dialog.setContentView(view)

        val tvTitle = view.findViewById<TextView>(R.id.tvModalTitle)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnCancelModal)
        val pbLoading = view.findViewById<ProgressBar>(R.id.pbLoading)
        val webView = view.findViewById<WebView>(R.id.wvPopPay)

        tvTitle.text = "PopPay Checkout · ₹$amount"

        btnCancel.setOnClickListener {
            dialog.dismiss()
            callback.onDismiss()
        }

        // Configure in-app WebView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = true
        }

        // JavaScript interface to receive postMessage events from PopPay checkout.js / iframe
        class WebAppInterface {
            @JavascriptInterface
            fun onPaymentSuccess(jsonStr: String) {
                try {
                    val obj = JSONObject(jsonStr)
                    val oId = obj.optString("order_id", orderId)
                    val pId = obj.optString("payment_id", "")
                    val utr = obj.optString("utr", "")
                    val sig = obj.optString("signature", "")
                    view.post {
                        dialog.dismiss()
                        callback.onPaymentSuccess(oId, pId, utr, sig)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            @JavascriptInterface
            fun onModalClosed() {
                view.post {
                    dialog.dismiss()
                    callback.onDismiss()
                }
            }
        }

        webView.addJavascriptInterface(WebAppInterface(), "AndroidPopPay")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false

                // Intercept UPI deep link (e.g. upi://pay?pa=...) to launch user's UPI app directly
                if (url.startsWith("upi://") || url.startsWith("intent://")) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(Intent.createChooser(intent, "Pay via UPI App"))
                        return true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                pbLoading.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                pbLoading.visibility = View.GONE
                // Inject message listener for PopPay postMessage payment.success and modal.closed
                val jsHook = """
                    window.addEventListener('message', function(event) {
                        if (event.data && typeof event.data === 'object') {
                            if (event.data.type === 'payment.success') {
                                AndroidPopPay.onPaymentSuccess(JSON.stringify(event.data));
                            } else if (event.data.reason === 'dismiss' || event.data.type === 'modal.closed') {
                                AndroidPopPay.onModalClosed();
                            }
                        }
                    });
                """.trimIndent()
                view?.evaluateJavascript(jsHook, null)
            }
        }

        webView.loadUrl(checkoutUrl)
        dialog.show()
    }
}
