package com.example.explicitwordsfilter

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.explicitwordsfilter.ui.theme.ExplicitWordsFilterTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExplicitWordsFilterTheme {
                SettingsScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var currentCode by remember { mutableStateOf("") }
    var newCode by remember { mutableStateOf("") }
    var confirmNewCode by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") }
            )
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = currentCode,
                    onValueChange = { currentCode = it },
                    label = { Text("Current Code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation()
                )

                OutlinedTextField(
                    value = newCode,
                    onValueChange = { newCode = it },
                    label = { Text("New Code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation()
                )

                OutlinedTextField(
                    value = confirmNewCode,
                    onValueChange = { confirmNewCode = it },
                    label = { Text("Confirm New Code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation()
                )

                Button(
                    onClick = {
                        if (currentCode == CodeManager.getCode(context)) {
                            if (newCode == confirmNewCode && newCode.isNotEmpty()) {
                                CodeManager.saveCode(newCode, context)
                                Toast.makeText(context, "Code changed successfully", Toast.LENGTH_SHORT).show()
                                // Optionally, navigate back or clear fields
                                currentCode = ""
                                newCode = ""
                                confirmNewCode = ""
                            } else {
                                Toast.makeText(context, "New codes do not match or are empty", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Incorrect current code", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("Change Code")
                }
            }
        }
    )
}
