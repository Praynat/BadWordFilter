package com.example.explicitwordsfilter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import android.app.NotificationManager
import android.app.PendingIntent
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo

class AccessibilityStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // We are responding to the action for accessibility settings change
        if (intent.action == Settings.ACTION_ACCESSIBILITY_SETTINGS) {
            Log.d("AccessibilityStateReceiver", "Accessibility settings opened, checking state")

            // Check if the ExplicitWordFilterService is enabled
            val isEnabled = isAccessibilityServiceEnabled(context, ExplicitWordFilterService::class.java)

            // Update the notification based on the accessibility state
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationText = if (isEnabled) {
                "The filter is running"
            } else {
                "Accessibility service is disabled. Tap to enable."
            }

            // Intent to open Accessibility Settings
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                PendingIntent.FLAG_UPDATE_CURRENT
            )

            // Build the notification
            val notification = NotificationCompat.Builder(context, WordFilterForegroundService.CHANNEL_ID)
                .setContentTitle("Explicit Words Filter")
                .setContentText(notificationText)
                .setSmallIcon(R.drawable.ic_notification) // Ensure the icon exists in drawable
                .setContentIntent(pendingIntent)
                .setOngoing(true) // Persistent notification
                .build()

            // Notify the user to enable accessibility service
            notificationManager.notify(WordFilterForegroundService.SERVICE_NOTIFICATION_ID, notification)
        }
    }

    // Check if the accessibility service is enabled
    private fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<out AccessibilityService>): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
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
}
