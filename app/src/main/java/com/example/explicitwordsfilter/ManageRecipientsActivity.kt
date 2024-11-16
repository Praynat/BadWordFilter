package com.example.explicitwordsfilter

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.explicitwordsfilter.ui.theme.ExplicitWordsFilterTheme
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
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

class ManageRecipientsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExplicitWordsFilterTheme {
                RecipientsScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipientsScreen() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    var emailList by remember { mutableStateOf(loadEmailList(prefs)) }
    var newEmail by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Recipients") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = newEmail,
                onValueChange = { newEmail = it },
                label = { Text("Enter Email Address") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    if (newEmail.isNotBlank()) {
                        if (android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
                            emailList = emailList + newEmail
                            saveEmailList(prefs, emailList)
                            sendNotificationToEmail(context, "recipient_added", "You have been added as a referee.", newEmail)
                            Toast.makeText(context, "Email added", Toast.LENGTH_SHORT).show()
                            newEmail = ""
                        } else {
                            Toast.makeText(context, "Invalid email address", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Add Email")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Current Recipients:")

            LazyColumn {
                items(emailList) { email ->
                    RecipientItem(email = email, onDelete = {
                        emailList = emailList - email
                        saveEmailList(prefs, emailList)
                        sendNotificationToEmail(context, "recipient_removed", "You have been removed as a referee.", email)
                        Toast.makeText(context, "Email removed", Toast.LENGTH_SHORT).show()
                    })
                }
            }
        }
    }
}

@Composable
fun RecipientItem(email: String, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = email)
        IconButton(onClick = onDelete) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
        }
    }
}

fun loadEmailList(prefs: android.content.SharedPreferences): List<String> {
    val emails = prefs.getStringSet("email_list", setOf()) ?: setOf()
    return emails.toList()
}

fun saveEmailList(prefs: android.content.SharedPreferences, emailList: List<String>) {
    prefs.edit().putStringSet("email_list", emailList.toSet()).apply()
}

fun sendNotificationToEmail(context: Context, eventType: String, details: String, email: String) {
    // Implement this function to send a notification to the specific email address
    // You can use a similar approach as notifyFriend, but specify the recipient email
    val client = OkHttpClient()

    try {
        val url = "https://script.google.com/macros/s/AKfycbwZ66VzHDxsBG0NE4xdDcDAHT9U5-F5BcIu6B4RRDVB1G6NoCU8SyrYmxYJjM_F8MskMg/exec"

        val json = JSONObject()
        json.put("secret", "bC7g5vK9pX2nT8rJ4qU1sL6fV0wE3hZ")
        json.put("eventType", eventType)
        json.put("details", details)
        json.put("recipient", email)

        val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ManageRecipients", "Failed to send notification", e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d("ManageRecipients", "Notification sent successfully")
                } else {
                    Log.e("ManageRecipients", "Failed to send notification: ${response.code} - ${response.message}")
                }
                response.close()
            }
        })
    } catch (e: Exception) {
        Log.e("ManageRecipients", "Exception in sendNotificationToEmail", e)
    }
}
