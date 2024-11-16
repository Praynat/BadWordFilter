package com.example.explicitwordsfilter

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

object CodeManager {
    private const val PREFS_NAME = "AppPrefs"
    private const val CODE_KEY = "disableCode"
    private const val DEFAULT_CODE = "1234"

    private fun getSharedPreferences(context: Context): SharedPreferences {
        // Use EncryptedSharedPreferences for better security
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveCode(newCode: String, context: Context) {
        val prefs = getSharedPreferences(context)
        prefs.edit().putString(CODE_KEY, newCode).apply()
    }

    fun getCode(context: Context): String {
        val prefs = getSharedPreferences(context)
        return prefs.getString(CODE_KEY, DEFAULT_CODE) ?: DEFAULT_CODE
    }
}
