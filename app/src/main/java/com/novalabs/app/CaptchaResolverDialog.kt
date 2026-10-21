package com.novalabs.app

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
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

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            // Standard mobile Chrome UA ensures hCaptcha JS scripts execute cleanly
            userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
        }
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

        val escapedRqData = if (!rqData.isNullOrEmpty()) {
            rqData.replace("\"", "&quot;")
        } else null

        val rqDataAttribute = if (!escapedRqData.isNullOrEmpty()) "data-rqdata=\"$escapedRqData\"" else ""

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
              <script src="https://js.hcaptcha.com/1/api.js" async defer></script>
              <style>
                html, body {
                  background-color: #121214;
                  color: #FFFFFF;
                  margin: 0;
                  padding: 0;
                  height: 100%;
                  display: flex;
                  flex-direction: column;
                  justify-content: center;
                  align-items: center;
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                }
                .hint {
                  margin-bottom: 24px;
                  font-size: 15px;
                  color: #949BA4;
                  text-align: center;
                }
                .h-captcha {
                  margin: 0 auto;
                }
              </style>
            </head>
            <body>
              <div class="hint">Discord Security Check<br><small>Solve the puzzle to finish logging in</small></div>
              <div class="h-captcha" data-sitekey="$siteKey" data-callback="onCaptchaResolved" $rqDataAttribute></div>
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
                1200
            )
            addView(webView)
        }

        dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Discord Verification")
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
