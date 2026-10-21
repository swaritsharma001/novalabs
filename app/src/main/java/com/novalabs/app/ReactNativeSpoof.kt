package com.novalabs.app

import android.content.Context
import android.os.Build
import android.util.Base64
import org.json.JSONObject
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

object ReactNativeSpoof {

    private const val CLIENT_BUILD_NUMBER = 273012
    private const val CLIENT_BUILD_NUMBER_FULL = 27301200250920L
    private const val USER_AGENT = "Discord-Android/$CLIENT_BUILD_NUMBER;RNA"
    private const val CLIENT_VERSION = "273.12 - rn"

    fun userAgent(): String = USER_AGENT

    private val deviceName: String
        get() = Build.PRODUCT

    val systemLocale: String
        get() = Locale.getDefault().let { it.language + if (it.country.isEmpty()) "" else "-" + it.country }

    val deviceTimezone: String
        get() = TimeZone.getDefault().id

    fun getDeviceVendorId(context: Context): String {
        val prefs = context.getSharedPreferences("rn_device_prefs", Context.MODE_PRIVATE)
        var id = prefs.getString("rn_device_vendor_id", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString("rn_device_vendor_id", id).apply()
        }
        return id
    }

    fun makeSuperProperties(context: Context): String {
        val json = JSONObject().apply {
            put("os", "Android")
            put("browser", "Discord Android")
            put("device", deviceName)
            put("system_locale", systemLocale)
            put("has_client_mods", false)
            put("client_version", CLIENT_VERSION)
            put("release_channel", "googleRelease")
            put("device_vendor_id", getDeviceVendorId(context))
            put("design_id", 2)
            put("browser_user_agent", "")
            put("browser_version", "")
            put("os_version", Build.VERSION.SDK_INT.toString())
            put("client_build_number", CLIENT_BUILD_NUMBER_FULL)
            put("client_event_source", JSONObject.NULL)
        }
        return Base64.encodeToString(json.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    fun makeHeaderMap(context: Context, authToken: String? = null, fingerprint: String? = null): Map<String, String> {
        val ret = LinkedHashMap<String, String>()
        if (!authToken.isNullOrEmpty()) {
            ret["authorization"] = authToken
        }
        ret["User-Agent"] = USER_AGENT
        ret["accept-language"] = systemLocale
        ret["x-discord-locale"] = systemLocale
        ret["x-discord-timezone"] = deviceTimezone

        if (!fingerprint.isNullOrEmpty()) {
            ret["x-fingerprint"] = fingerprint
        }

        ret["x-super-properties"] = makeSuperProperties(context)
        return ret
    }
}
