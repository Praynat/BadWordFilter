package com.example.explicitwordsfilter

import android.content.Context
import android.content.SharedPreferences

object PasswordManager {
    private const val PREFS_NAME = "app_prefs"
    private const val PASSWORD_KEY = "user_password"

    fun getPassword(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PASSWORD_KEY, "1234") ?: "1234" // Default to "1234"
    }

    fun savePassword(context: Context, newPassword: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PASSWORD_KEY, newPassword).apply()
    }
}
