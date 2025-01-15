package com.example.explicitwordsfilter

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.explicitwordsfilter.ui.theme.ExplicitWordsFilterTheme

class AddWordActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExplicitWordsFilterTheme {
                AddWordScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWordScreen() {
    var word by remember { mutableStateOf("") }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Word to Block") }
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
            OutlinedTextField(
                value = word,
                onValueChange = { word = it },
                label = { Text("Enter a word to block") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    if (word.isNotBlank()) {
                        saveUserAddedWord(word, context)
                        word = "" // Clear the text field
                        Toast.makeText(context, "Word added to block list", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Please enter a word", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Add Word")
            }
        }
    }
}

fun saveUserAddedWord(word: String, context: Context) {
    val prefs = context.getSharedPreferences(SharedPrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
    val editor = prefs.edit()
    val wordsSet = prefs.getStringSet(SharedPrefsConstants.USER_WORDS_KEY, mutableSetOf()) ?: mutableSetOf()
    wordsSet.add(word)
    editor.putStringSet(SharedPrefsConstants.USER_WORDS_KEY, wordsSet)
    editor.apply()
}
