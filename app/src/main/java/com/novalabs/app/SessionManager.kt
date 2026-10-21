package com.novalabs.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class UserSession(
    val id: String,
    val username: String,
    val token: String,
    val avatarUrl: String? = null,
    val isPremium: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)

object SessionManager {

    private const val PREFS_NAME = "nxt_sessions_prefs"
    private const val KEY_SESSIONS = "saved_sessions"
    private const val KEY_ACTIVE_ID = "active_session_id"

    fun getSessions(context: Context): List<UserSession> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        val list = mutableListOf<UserSession>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    UserSession(
                        id = obj.getString("id"),
                        username = obj.getString("username"),
                        token = obj.getString("token"),
                        avatarUrl = obj.optString("avatarUrl", null),
                        isPremium = obj.optBoolean("isPremium", false),
                        addedAt = obj.optLong("addedAt", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun saveSession(context: Context, session: UserSession, setActive: Boolean = true) {
        val sessions = getSessions(context).toMutableList()
        sessions.removeAll { it.id == session.id }
        sessions.add(0, session)

        val arr = JSONArray()
        sessions.forEach { s ->
            val obj = JSONObject().apply {
                put("id", s.id)
                put("username", s.username)
                put("token", s.token)
                put("avatarUrl", s.avatarUrl ?: JSONObject.NULL)
                put("isPremium", s.isPremium)
                put("addedAt", s.addedAt)
            }
            arr.put(obj)
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        prefs.putString(KEY_SESSIONS, arr.toString())
        if (setActive) {
            prefs.putString(KEY_ACTIVE_ID, session.id)
        }
        prefs.apply()
    }

    fun getActiveSession(context: Context): UserSession? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val activeId = prefs.getString(KEY_ACTIVE_ID, null)
        val sessions = getSessions(context)
        return sessions.firstOrNull { it.id == activeId } ?: sessions.firstOrNull()
    }

    fun setActiveSession(context: Context, sessionId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_ID, sessionId)
            .apply()
    }

    fun removeSession(context: Context, sessionId: String) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sessions = getSessions(context).filter { it.id != sessionId }
        val arr = JSONArray()
        sessions.forEach { s ->
            val obj = JSONObject().apply {
                put("id", s.id)
                put("username", s.username)
                put("token", s.token)
                put("avatarUrl", s.avatarUrl ?: JSONObject.NULL)
                put("isPremium", s.isPremium)
                put("addedAt", s.addedAt)
            }
            arr.put(obj)
        }

        val editor = sharedPrefs.edit()
        editor.putString(KEY_SESSIONS, arr.toString())
        if (sharedPrefs.getString(KEY_ACTIVE_ID, null) == sessionId) {
            editor.putString(KEY_ACTIVE_ID, sessions.firstOrNull()?.id)
        }
        editor.apply()
    }
}
