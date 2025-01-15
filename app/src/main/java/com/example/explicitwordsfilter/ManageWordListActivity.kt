package com.example.explicitwordsfilter

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("user_words", setOf("test1", "test2", "test3")).apply()


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
    val prefs = activity.getSharedPreferences(SharedPrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
    var wordList by remember {
        mutableStateOf(
            prefs.getStringSet(SharedPrefsConstants.USER_WORDS_KEY, emptySet())?.toList() ?: listOf()
        )
    }

    // State for editing
    var editingWord by remember { mutableStateOf<String?>(null) }
    var editedWord by remember { mutableStateOf("") }

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
                // If editing, show the input field
                if (editingWord == word) {
                    OutlinedTextField(
                        value = editedWord,
                        onValueChange = { editedWord = it },
                        label = { Text("Edit word") },
                        modifier = Modifier.weight(1f)
                    )

                    Row {
                        // Save button
                        IconButton(onClick = {
                            if (editedWord.isNotBlank()) {
                                val updatedList = wordList.toMutableList()
                                updatedList.remove(word)
                                updatedList.add(editedWord)
                                wordList = updatedList
                                saveWordList(prefs, updatedList)
                                editingWord = null // Reset editing state
                                editedWord = "" // Clear edited word
                            }
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Save")
                        }

                        // Cancel button
                        IconButton(onClick = {
                            editingWord = null // Reset editing state
                            editedWord = "" // Clear edited word
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    }
                } else {
                    // Normal view of word
                    Text(text = word, modifier = Modifier.weight(1f))

                    Row {
                        // Edit button
                        IconButton(onClick = {
                            editingWord = word // Set the word being edited
                            editedWord = word // Set the initial value to the current word
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }

                        // Delete button
                        IconButton(onClick = {
                            val updatedList = wordList - word
                            wordList = updatedList
                            saveWordList(prefs, updatedList)
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}


fun saveWordList(prefs: SharedPreferences, wordList: List<String>) {
    prefs.edit().putStringSet(SharedPrefsConstants.USER_WORDS_KEY, wordList.toSet()).apply()
}

