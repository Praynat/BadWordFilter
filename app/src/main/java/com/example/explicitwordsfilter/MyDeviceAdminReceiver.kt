package com.example.explicitwordsfilter

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d("DeviceAdminReceiver", "Device Admin Disabled - Possible App Uninstall")
        notifyFriendAppUninstalled(context)
    }

    private fun notifyFriendAppUninstalled(context: Context) {
        val url = "https://script.google.com/macros/s/your_script_id/exec" // Replace with your script URL

        val json = JSONObject()
        json.put("secret", "your_secret_key") // Same as in your Apps Script
        json.put("eventType", "app_uninstalled")
        json.put("details", "The app has been uninstalled from the device.")

        val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("DeviceAdminReceiver", "Failed to send uninstall notification: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d("DeviceAdminReceiver", "Uninstall notification sent successfully")
                } else {
                    Log.e("DeviceAdminReceiver", "Failed to send uninstall notification: ${response.message}")
                }
                response.close()
            }
        })
    }
}
