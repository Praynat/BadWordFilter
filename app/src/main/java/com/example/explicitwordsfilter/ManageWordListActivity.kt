package com.example.explicitwordsfilter

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.explicitwordsfilter.ui.theme.ExplicitWordsFilterTheme

class ManageWordListActivity : ComponentActivity() {

    private var isPasswordCorrect by mutableStateOf(false) // Keep track of password state

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ExplicitWordsFilterTheme {
                if (isPasswordCorrect) {
                    ManageWordListScreen(activity = this)
                } else {
                    PasswordEntryScreen { enteredPassword ->
                        val savedPassword = PasswordManager.getPassword(this)

                        if (savedPassword == enteredPassword) {
                            Toast.makeText(this, "Password correct", Toast.LENGTH_SHORT).show()
                            isPasswordCorrect = true
                        } else {
                            Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordEntryScreen(onPasswordEntered: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Enter Password to Manage Word List")

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (password.isNotEmpty()) {
                    onPasswordEntered(password)
                } else {
                    Toast.makeText(context, "Please enter the password", Toast.LENGTH_SHORT).show()
                }
            }
        ) {
            Text("Submit")
        }
    }
}

@Composable
fun ManageWordListScreen(activity: ComponentActivity) {
    val prefs = LocalContext.current.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    var wordList by remember { mutableStateOf(prefs.getStringSet("user_words", emptySet())?.toList() ?: listOf()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Back button
        IconButton(onClick = {
            activity.finish() // Call finish on the passed activity
        }) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Manage Word List", style = MaterialTheme.typography.titleLarge)

        Spacer(modifier = Modifier.height(16.dp))

        wordList.forEach { word ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = word)

                Row {
                    // Edit button (optional)
                    IconButton(onClick = {
                        // Implement edit functionality here
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }

                    // Delete button
                    IconButton(onClick = {
                        wordList = wordList - word
                        saveWordList(prefs, wordList)
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

fun saveWordList(prefs: SharedPreferences, wordList: List<String>) {
    prefs.edit().putStringSet("user_words", wordList.toSet()).apply()
}
