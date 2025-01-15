package com.example.explicitwordsfilter

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class WordFilterForegroundService : Service(), AccessibilityManager.AccessibilityStateChangeListener {

    companion object {
        const val CHANNEL_ID = "WordFilterServiceChannel"
        const val SERVICE_NOTIFICATION_ID = 1
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var accessibilityManager: AccessibilityManager
    private lateinit var accessibilityStateReceiver: AccessibilityStateReceiver
    private val client = OkHttpClient()
    private var hasNotifiedAccessibilityOff = false

    override fun onCreate() {
        super.onCreate()
        Log.d("WordFilterService", "Service Created")
        createNotificationChannel()
        startForeground(SERVICE_NOTIFICATION_ID, getNotification())
        checkForForceStop()
        // Initialize AccessibilityManager and register the listener
        accessibilityManager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        accessibilityManager.addAccessibilityStateChangeListener(this)
        Log.d("WordFilterService", "AccessibilityStateChangeListener registered")

        // Initialize the broadcast receiver
        accessibilityStateReceiver = AccessibilityStateReceiver()
        val filter = IntentFilter(Settings.ACTION_ACCESSIBILITY_SETTINGS)

        // Conditionally register the receiver based on API level
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // API 26 and above
            registerReceiver(accessibilityStateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(accessibilityStateReceiver, filter) // No flag for lower API levels
        }

        // Periodically check the accessibility service status every 5 seconds
        serviceScope.launch {
            while (isActive) {
                delay(5000) // Check every 5 seconds
                val isAccessibilityEnabled = isAccessibilityServiceEnabled(this@WordFilterForegroundService, ExplicitWordFilterService::class.java)
                updateNotification(isAccessibilityEnabled)

                if (!isAccessibilityEnabled && !hasNotifiedAccessibilityOff) {
                    // Show notification to prompt the user if accessibility is disabled
                    showAccessibilityDisabledNotification()
                    notifyFriend("accessibility_off", "Le Service d'accessibilité a été arreté (l'appareil n'est plus protégé!")
                    hasNotifiedAccessibilityOff = true
                }

                if (isAccessibilityEnabled && hasNotifiedAccessibilityOff) {
                    // Reset the flag when the accessibility service is re-enabled
                    hasNotifiedAccessibilityOff = false
                }
            }
        }


        // Initial notification update
        updateNotification(isAccessibilityServiceEnabled(this, ExplicitWordFilterService::class.java))
    }



    override fun onAccessibilityStateChanged(enabled: Boolean) {
        Log.d("WordFilterService", "Accessibility State Changed: $enabled")
        val isAccessibilityEnabled = isAccessibilityServiceEnabled(this, ExplicitWordFilterService::class.java)
        Log.d("WordFilterService", "isAccessibilityEnabled: $isAccessibilityEnabled")
        updateNotification(isAccessibilityEnabled)

        if (!isAccessibilityEnabled) {
            // Prompt the user if accessibility is disabled
            showAccessibilityDisabledNotification()
            notifyFriend("accessibility_off", "Le Service d'accessibilité a été arreté (l'appareil n'est plus protégé!)")
        }
    }

    private fun showAccessibilityDisabledNotification() {
        val notificationText = "Accessibility service is disabled. Message sent. Tap to enable."

        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Explicit Words Filter")
            .setContentText(notificationText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(SERVICE_NOTIFICATION_ID, notification)
    }



    private fun updateNotification(isAccessibilityEnabled: Boolean) {
        val notificationText = if (isAccessibilityEnabled) {
            "The filter is running"
        } else {
            "Accessibility service is disabled. Message sent. Tap to enable."
        }

        val intent = if (isAccessibilityEnabled) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Explicit Words Filter")
            .setContentText(notificationText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(SERVICE_NOTIFICATION_ID, notification)
    }


    private fun isAccessibilityServiceEnabled(
        context: Context,
        serviceClass: Class<out AccessibilityService>
    ): Boolean {
        val am = context.getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (service in enabledServices) {
            if (service.resolveInfo.serviceInfo.packageName == context.packageName &&
                service.resolveInfo.serviceInfo.name == serviceClass.name
            ) {
                return true
            }
        }
        return false
    }

    private fun notifyFriend(eventType: String, details: String) {
        Log.d("WordFilterService", "notifyFriend called with eventType: $eventType")

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val emailList = prefs.getStringSet("email_list", setOf())?.toList() ?: listOf()

        if (emailList.isEmpty()) {
            Log.d("WordFilterService", "No recipients to notify")
            return
        }

        // Loop through all the email addresses and send a notification to each
        for (email in emailList) {
            sendNotificationToEmail(eventType, details, email)
        }
    }

    private fun sendNotificationToEmail(eventType: String, details: String, email: String) {
        Log.d("WordFilterService", "Sending notification to $email for eventType: $eventType")

        try {
            val url = "https://script.google.com/macros/s/AKfycbzacPVyP_XiFlLqkh82ykZ2JveOvT06lLhVHnMNYaeeovphIFqJvDl605sMBHBJ6ro4OA/exec" // Replace with your script URL

            val json = JSONObject()
            json.put("secret", "bC7g5vK9pX2nT8rJ4qU1sL6fV0wE3hZ") // Same as in your Apps Script
            json.put("eventType", eventType)
            json.put("details", details)


            val recipients = listOf(email)
            json.put("recipients", JSONArray(recipients))


            val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("WordFilterService", "Failed to send notification to $email", e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        Log.d("WordFilterService", "Notification sent successfully to $email")
                    } else {
                        Log.e("WordFilterService", "Failed to send notification to $email: ${response.code} - ${response.message}")
                    }
                    response.close()
                }
            })
        } catch (e: Exception) {
            Log.e("WordFilterService", "Exception in sendNotificationToEmail", e)
        }
    }




    private fun checkForForceStop() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val lastRunTime = prefs.getLong("last_run_time", 0L)
        val currentTime = System.currentTimeMillis()

        // Define a threshold (e.g., 5 minutes)
        val threshold = 5 * 60 * 1000L

        if (currentTime - lastRunTime > threshold && lastRunTime != 0L) {
            // Assume the app was force stopped
            notifyFriend("app_force_stopped", "L'application a été arretée ou a crashée ")
        }

        // Update the timestamp
        prefs.edit().putLong("last_run_time", currentTime).apply()
    }


    override fun onDestroy() {
        super.onDestroy()
        Log.d("WordFilterService", "Service Destroyed")
        accessibilityManager.removeAccessibilityStateChangeListener(this)
        serviceScope.cancel()

        // Unregister the broadcast receiver to prevent leaks
        unregisterReceiver(accessibilityStateReceiver)
    }



    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("WordFilterService", "Service Started via onStartCommand")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Word Filter Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Channel for Word Filter Foreground Service"
                enableLights(true)
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            Log.d("WordFilterService", "Notification Channel Created")
        }
    }

    private fun getNotification(): Notification {
        val isAccessibilityEnabled = isAccessibilityServiceEnabled(this, ExplicitWordFilterService::class.java)
        val notificationText = if (isAccessibilityEnabled) {
            "The filter is running"
        } else {
            "Accessibility service is disabled. Tap to enable."
        }

        val intent = if (isAccessibilityEnabled) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Explicit Words Filter")
            .setContentText(notificationText)
            .setSmallIcon(R.drawable.ic_notification) // Ensure this icon exists
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

}
