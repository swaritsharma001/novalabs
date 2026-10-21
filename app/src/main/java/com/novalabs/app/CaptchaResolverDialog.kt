package com.novalabs.app

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object CaptchaResolverDialog {

    interface CaptchaCallback {
        fun onResolved(captchaKey: String)
        fun onCancelled()
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun show(
        context: Context,
        siteKey: String,
        rqData: String? = null,
        callback: CaptchaCallback
    ) {
        val webView = WebView(context)
        var dialog: AlertDialog? = null

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.setBackgroundColor(0xFF121214.toInt())

        class CaptchaJsInterface {
            @JavascriptInterface
            fun onTokenReceived(token: String) {
                webView.post {
                    dialog?.dismiss()
                    callback.onResolved(token)
                }
            }
        }

        webView.addJavascriptInterface(CaptchaJsInterface(), "AndroidCaptcha")
        webView.webViewClient = object : WebViewClient() {}

        val rqDataAttr = if (!rqData.isNullOrEmpty()) "data-rqdata=\"$rqData\"" else ""

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
              <script src="https://js.hcaptcha.com/1/api.js" async defer></script>
              <style>
                body {
                  background-color: #121214;
                  color: #FFFFFF;
                  display: flex;
                  flex-direction: column;
                  justify-content: center;
                  align-items: center;
                  height: 100vh;
                  margin: 0;
                  font-family: sans-serif;
                }
                .hint {
                  margin-bottom: 20px;
                  font-size: 14px;
                  color: #949BA4;
                }
              </style>
            </head>
            <body>
              <div class="hint">Please solve the captcha to continue</div>
              <div class="h-captcha" data-sitekey="$siteKey" data-callback="onCaptchaResolved" $rqDataAttr></div>
              <script>
                function onCaptchaResolved(token) {
                  if (window.AndroidCaptcha) {
                    window.AndroidCaptcha.onTokenReceived(token);
                  }
                }
              </script>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL("https://discord.com", html, "text/html", "UTF-8", null)

        val container = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                1100
            )
            addView(webView)
        }

        dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Verification Required")
            .setView(container)
            .setNegativeButton("Cancel") { _, _ ->
                callback.onCancelled()
            }
            .setOnCancelListener {
                callback.onCancelled()
            }
            .create()

        dialog.show()
    }
}
