package com.example.explicitwordsfilter

import android.app.admin.DevicePolicyManager
import android.content.ComponentName

import androidx.activity.compose.setContent
import com.example.explicitwordsfilter.ui.theme.ExplicitWordsFilterTheme
import android.util.Log

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.explicitwordsfilter.ui.theme.ExplicitWordsFilterTheme
import com.example.explicitwordsfilter.CodeManager
import androidx.compose.ui.text.input.PasswordVisualTransformation
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlertDialog
import android.content.SharedPreferences
import android.view.accessibility.AccessibilityManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException


class MainActivity : ComponentActivity() {

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var compName: ComponentName



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate: Starting Foreground Service")
        startWordFilterForegroundService()

        // Initialize DevicePolicyManager and ComponentName
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        compName = ComponentName(this, MyDeviceAdminReceiver::class.java)

        // Check if Device Admin is not active
        if (!devicePolicyManager.isAdminActive(compName)) {
            // Prompt the user to activate Device Admin
            showDeviceAdminDialog()
        }

        setContent {
            ExplicitWordsFilterTheme {
                MainScreen(
                    notifyFriend = { eventType, details -> notifyFriend(eventType, details) },
                    changePassword = { newPassword ->
                        PasswordManager.savePassword(this, newPassword)
                        Toast.makeText(this, "Password changed", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }


    private fun showDeviceAdminDialog() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName)
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Device Administrator permission is required to monitor app uninstallation.")
        startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN)
    }

    companion object {
        private const val REQUEST_CODE_ENABLE_ADMIN = 1
    }

    private fun startWordFilterForegroundService() {
        val serviceIntent = Intent(this, WordFilterForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d("MainActivity", "Starting Foreground Service with startForegroundService")
            startForegroundService(serviceIntent)
        } else {
            Log.d("MainActivity", "Starting Foreground Service with startService")
            startService(serviceIntent)
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_ENABLE_ADMIN) {
            if (devicePolicyManager.isAdminActive(compName)) {
                // Device Admin activated
                Toast.makeText(this, "Device Admin activated.", Toast.LENGTH_SHORT).show()
            } else {
                // Activation failed
                Toast.makeText(this, "Device Admin activation failed.", Toast.LENGTH_SHORT).show()
            }
        }
    }


    fun isAccessibilityServiceEnabled(
        context: Context,
        serviceClass: Class<out AccessibilityService>
    ): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices =
            am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (service in enabledServices) {
            if (service.resolveInfo.serviceInfo.packageName == context.packageName &&
                service.resolveInfo.serviceInfo.name == serviceClass.name
            ) {
                return true
            }
        }
        return false
    }

    override fun onResume() {
        super.onResume()
        checkAccessibilityService()
    }
    private fun checkAccessibilityService() {
        val isEnabled = isAccessibilityServiceEnabled(this, ExplicitWordFilterService::class.java)
        if (!isEnabled) {
            showAccessibilityServiceDialog()
        }
    }
    private fun showAccessibilityServiceDialog() {
        AlertDialog.Builder(this)
            .setTitle("Enable Accessibility Service")
            .setMessage("The Explicit Words Filter requires accessibility permission to function properly. Please enable it in settings.")
            .setPositiveButton("Enable") { dialog, which ->
                // Navigate to accessibility settings
                openAccessibilitySettings()
            }
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .show()
    }
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }
    fun notifyFriend(eventType: String, details: String) {
        val client = OkHttpClient()

        try {
            // Get the saved email list from SharedPreferences
            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val emailList = prefs.getStringSet("email_list", setOf())?.toList() ?: listOf()

            // Ensure there are no duplicate email addresses
            val uniqueEmailList = emailList.distinct()

            // Log the email list to verify
            Log.d("MainActivity", "Email list: $uniqueEmailList")

            // If the email list is empty, log and return
            if (uniqueEmailList.isEmpty()) {
                Log.d("MainActivity", "No recipients to notify")
                return
            }

            // Loop through the unique email addresses and send a notification to each
            for (email in uniqueEmailList) {
                // Pass the current context (`this`) when calling sendNotificationToEmail
                sendNotificationToEmail(this, eventType, details, email)
            }

        } catch (e: Exception) {
            Log.e("MainActivity", "Exception in notifyFriend", e)
        }
    }

    fun sendNotificationToEmail(context: Context, eventType: String, details: String, email: String) {
        val client = OkHttpClient()

        try {
            val url = "https://script.google.com/macros/s/AKfycbwZ66VzHDxsBG0NE4xdDcDAHT9U5-F5BcIu6B4RRDVB1G6NoCU8SyrYmxYJjM_F8MskMg/exec"

            val json = JSONObject()
            json.put("secret", "bC7g5vK9pX2nT8rJ4qU1sL6fV0wE3hZ")
            json.put("eventType", eventType)
            json.put("details", details)

            // Add the recipient email to the JSON payload
            json.put("recipient", email)

            val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("MainActivity", "Failed to send notification to $email", e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        Log.d("MainActivity", "Notification sent successfully to $email")
                    } else {
                        Log.e("MainActivity", "Failed to send notification: ${response.code} - ${response.message}")
                    }
                    response.close()
                }
            })
        } catch (e: Exception) {
            Log.e("MainActivity", "Exception in sendNotificationToEmail", e)
        }
    }




}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    notifyFriend: (String, String) -> Unit,
    changePassword: (String) -> Unit
) {
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var isFilterEnabled by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // State for showing the code entry dialog
    var showCodeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Explicit Words Filter") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Top
        ) {
            Text(text = "Filter Options", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))

            // Switch for enabling/disabling the filter
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Switch(
                    checked = isFilterEnabled,
                    onCheckedChange = { isChecked ->
                        if (isChecked) {
                            isFilterEnabled = true
                            val serviceIntent = Intent(context, WordFilterForegroundService::class.java)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                            Toast.makeText(context, "Filter Enabled", Toast.LENGTH_SHORT).show()
                        } else {
                            showCodeDialog = true
                        }
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = if (isFilterEnabled) "Filter Enabled" else "Filter Disabled")
            }

            Text(text = "Word List", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))

            // Button to add words to block list (no password required)
            Button(
                onClick = {
                    val intent = Intent(context, AddWordActivity::class.java)
                    context.startActivity(intent)
                }
            ) {
                Text("Add Word to Block List")
            }
            Button(
                onClick = {
                    val intent = Intent(context, ManageWordListActivity::class.java)
                    context.startActivity(intent)
                },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Manage Word List")
            }


            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "Manage Referees", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))

            // Manage referees screen navigation
            Button(
                onClick = {
                    val intent = Intent(context, ManageRecipientsActivity::class.java)
                    context.startActivity(intent)
                }
            ) {
                Text("Manage Referees")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "Settings", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))


            // Add a button to open the Change Password Dialog
            Button(
                onClick = { showChangePasswordDialog = true },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Change Password")
            }
            if (showChangePasswordDialog) {
                ChangePasswordDialog(
                    onDismiss = { showChangePasswordDialog = false },
                    onConfirm = { currentPassword, newPassword ->
                        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        val savedPassword = PasswordManager.getPassword(context)
                        if (currentPassword == savedPassword) {
                            if (newPassword.isNotEmpty()) {
                                changePassword(newPassword)
                                Toast.makeText(context, "Password changed successfully", Toast.LENGTH_SHORT).show()
                                showChangePasswordDialog = false
                            } else {
                                Toast.makeText(context, "New password cannot be empty", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Current password is incorrect", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        // Code entry dialog for disabling the filter
        if (showCodeDialog) {
            DisableFilterDialog(
                onDismiss = { showCodeDialog = false },
                onConfirm = { codeEntered ->
                    if (codeEntered == PasswordManager.getPassword(context)) {
                        // Correct code, proceed to disable the filter
                        isFilterEnabled = false
                        val serviceIntent = Intent(context, WordFilterForegroundService::class.java)
                        context.stopService(serviceIntent)
                        showCodeDialog = false
                        Toast.makeText(context, "Filter Disabled", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Incorrect code", Toast.LENGTH_SHORT).show()
                    }

                }
            )
        }
    }
}



// Save the email list to SharedPreferences
fun saveEmailListInMainActivity (prefs: SharedPreferences, emailList: List<String>) {
    prefs.edit().putStringSet("email_list", emailList.toSet()).apply()
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Password") },
        text = {
            Column {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = { Text("Current Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(currentPassword, newPassword) }
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}




@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisableFilterDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Code to Disable Filter") },
        text = {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("Code") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(code) }
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}




